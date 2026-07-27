"""Merges a LoRA adapter into its base model and writes plain HF weights.

Run through merge-and-quantize.sh rather than directly; it sets up the
environment this needs.

The reason this is a script and not two lines of PEFT: when the adapter touches
`embed_tokens` and the base ties `lm_head` to that same tensor — which gemma3
does — a plain `merge_and_unload()` produces a model that repeats one token
forever. A LoRA on an embedding changes what the lookup returns, not the stored
weights, so while training the tied output head read the *base* embeddings.
Merging writes the delta into the tensor, and the head suddenly sees an update it
never saw. Untying, and leaving the output side on the original embeddings,
reproduces training exactly.
"""

import argparse
import shutil
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

# Files transformers does not always re-emit but llama.cpp's converter wants.
EXTRA_TOKENIZER_FILES = ("added_tokens.json", "tokenizer.model")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base", type=Path, help="directory holding the base model")
    parser.add_argument("adapter", type=Path, help="directory holding the LoRA adapter")
    parser.add_argument("output", type=Path, help="where to write the merged weights")
    parser.add_argument(
        "--probe",
        default="Translate this from Toki Pona to English:\nToki Pona: jan li moku e kili\nEnglish:",
        help="prompt generated after merging, as a smoke test",
    )
    return parser.parse_args()


def generate(model, tokenizer, prompt: str) -> str:
    inputs = tokenizer(prompt, return_tensors="pt")
    with torch.no_grad():
        out = model.generate(
            **inputs,
            max_new_tokens=32,
            do_sample=False,
            pad_token_id=tokenizer.pad_token_id or tokenizer.eos_token_id,
        )
    return tokenizer.decode(out[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True).strip()


def main() -> None:
    args = parse_args()

    tokenizer = AutoTokenizer.from_pretrained(args.base)
    model = AutoModelForCausalLM.from_pretrained(args.base, dtype=torch.bfloat16)
    tied = bool(getattr(model.config, "tie_word_embeddings", False))
    original_embeddings = model.get_input_embeddings().weight.detach().clone()

    print(f"==> merging (tie_word_embeddings={tied})")
    model = PeftModel.from_pretrained(model, args.adapter)
    model = model.merge_and_unload()

    if tied:
        # See the module docstring. Skipping this is the difference between a
        # working translator and one that repeats a single token.
        print("==> untying the output head from the merged embeddings")
        model.config.tie_word_embeddings = False
        model.lm_head.weight = torch.nn.Parameter(original_embeddings)
        model.tie_weights = lambda: None  # stop save_pretrained re-tying them

    print(f"==> probe: {generate(model, tokenizer, args.probe)!r}")

    args.output.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(args.output, safe_serialization=True)
    tokenizer.save_pretrained(args.output)
    for name in EXTRA_TOKENIZER_FILES:
        source = args.base / name
        if source.exists() and not (args.output / name).exists():
            shutil.copy2(source, args.output / name)

    print(f"==> wrote {args.output}")


if __name__ == "__main__":
    main()
