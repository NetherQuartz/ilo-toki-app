#!/usr/bin/env python3
"""Runs one prompt set through several GGUF models and prints them side by side.

    scripts/compare-models.py --model 1.1=path/a.gguf --model 1.2=path/b.gguf \
        --out build/compare/result.json

Same quantization on every side or the comparison means nothing, and the prompt is
built exactly as `translationPrompt` builds it, so this is the format the app sends.
Decoding is greedy — `--temp 0 --top-k 1`, on the CPU — which makes every answer
reproducible and comparable across runs.

Build the runner once with:

    cmake -S llama.cpp -B build/compare/llama-build -DCMAKE_BUILD_TYPE=Release \
        -DLLAMA_BUILD_TOOLS=ON -DLLAMA_CURL=OFF
    cmake --build build/compare/llama-build --target llama-completion -j8

The set is three blocks. `general` is ordinary translation across the three
languages in both directions. `punct` is the same sentence bare and with its
terminal mark, as pairs, because a model that answers differently to a full stop is
a model that will surprise someone. `focus:*` are the constructions past releases
went wrong on, kept so the same questions get the same answers every time rather
than being re-invented per investigation.
"""

#!/usr/bin/env python3
"""Runs the same translation set through two GGUF models and prints them side by side.

Replicates the app exactly: `translationPrompt` in Translation.kt (SourceTarget
style), greedy decoding, 2048-token context, 512-token cap.
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path

BIN = Path("build/compare/llama-build/bin/llama-completion")

TOKI_PONA = "Toki Pona"


def prompt_for(text: str, from_tp: bool, other: str, style: str) -> str:
    source = TOKI_PONA if from_tp else other
    target = other if from_tp else TOKI_PONA
    q = text.strip()
    if style == "QueryAnswer":
        return f"Translate {source} to {target}.\nQuery: {q}\nAnswer:"
    return f"Translate this from {source} to {target}:\n{source}: {q}\n{target}:"


# (text, from_toki_pona, other_language, what a correct answer looks like)
CASES = [
    # ---- toki pona -> English -------------------------------------------
    ("jan li moku e kili", True, "English", "someone eats fruit"),
    ("soweli lili li lape lon tomo", True, "English", "a small animal sleeps in the house"),
    ("jan pona mi li kama tawa tomo mi lon tenpo pimeja", True, "English",
     "my friend came to my house at night"),
    ("mi wile e ni: sina toki tawa mi lon tenpo suno kama", True, "English",
     "I want you to talk to me tomorrow"),
    ("ilo mi li pakala la mi ken ala toki tawa sina", True, "English",
     "my device is broken so I cannot talk to you"),
    ("tenpo suno ni la mi pilin pona mute", True, "English", "today I feel very good"),
    ("sina sona ala sona e toki pona", True, "English", "do you know toki pona?"),
    ("mama mi li pali e moku pona lon tomo moku", True, "English",
     "my parent makes good food in the kitchen"),
    ("jan lili li lukin e waso lon sewi", True, "English",
     "the child watches a bird in the sky"),
    ("mi tawa ma ante lon tenpo mun kama", True, "English",
     "I am going to another country next month"),
    ("jan mute li kama lon tomo suli la mi pilin ike", True, "English",
     "when many people come into the big room I feel bad"),
    ("sina wile ala wile moku e telo suwi", True, "English", "do you want to drink juice?"),
    # ---- toki pona -> Russian -------------------------------------------
    ("jan li moku e kili", True, "Russian", "человек ест фрукт"),
    ("soweli lili li lape lon tomo", True, "Russian", "зверёк спит в доме"),
    ("jan pona mi li kama tawa tomo mi lon tenpo pimeja", True, "Russian",
     "мой друг пришёл ко мне домой ночью"),
    ("ilo mi li pakala la mi ken ala toki tawa sina", True, "Russian",
     "мой прибор сломался, поэтому я не могу с тобой говорить"),
    ("tenpo suno ni la mi pilin pona mute", True, "Russian", "сегодня мне очень хорошо"),
    ("sina sona ala sona e toki pona", True, "Russian", "ты знаешь ток и пона?"),
    ("mama mi li pali e moku pona lon tomo moku", True, "Russian",
     "мой родитель готовит вкусную еду на кухне"),
    ("mi tawa ma ante lon tenpo mun kama", True, "Russian",
     "в следующем месяце я еду в другую страну"),
    # ---- toki pona -> Vietnamese ----------------------------------------
    ("jan li moku e kili", True, "Vietnamese", "người ăn trái cây"),
    ("soweli lili li lape lon tomo", True, "Vietnamese", "con vật nhỏ ngủ trong nhà"),
    ("jan pona mi li kama tawa tomo mi lon tenpo pimeja", True, "Vietnamese",
     "bạn tôi đến nhà tôi vào ban đêm"),
    ("tenpo suno ni la mi pilin pona mute", True, "Vietnamese", "hôm nay tôi thấy rất khỏe"),
    ("sina sona ala sona e toki pona", True, "Vietnamese", "bạn có biết toki pona không?"),
    ("mi wile moku e telo", True, "Vietnamese", "tôi muốn uống nước"),
    # ---- English -> toki pona -------------------------------------------
    ("I love you", False, "English", "mi olin e sina"),
    ("The cat is sleeping on the table", False, "English", "soweli li lape lon supa"),
    ("My friend came to my house last night", False, "English",
     "jan pona mi li kama tawa tomo mi lon tenpo pimeja pini"),
    ("Do you want to eat something?", False, "English", "sina wile ala wile moku"),
    ("I cannot talk to you because my phone is broken", False, "English",
     "ilo mi li pakala la mi ken ala toki tawa sina"),
    ("Many people are walking in the big city", False, "English",
     "jan mute li tawa lon ma tomo suli"),
    ("The weather is very good today", False, "English", "tenpo suno ni la ma li pona"),
    ("What are you doing?", False, "English", "sina pali e seme"),
    # ---- Russian -> toki pona -------------------------------------------
    ("Я тебя люблю", False, "Russian", "mi olin e sina"),
    ("Кошка спит на столе", False, "Russian", "soweli li lape lon supa"),
    ("Мой друг пришёл ко мне домой вчера вечером", False, "Russian",
     "jan pona mi li kama tawa tomo mi lon tenpo pimeja pini"),
    ("Ты хочешь есть?", False, "Russian", "sina wile ala wile moku"),
    ("Сегодня очень хорошая погода", False, "Russian", "tenpo suno ni la ma li pona"),
    ("Что ты делаешь?", False, "Russian", "sina pali e seme"),
    # ---- Vietnamese -> toki pona ----------------------------------------
    ("Tôi yêu bạn", False, "Vietnamese", "mi olin e sina"),
    ("Con mèo đang ngủ trên bàn", False, "Vietnamese", "soweli li lape lon supa"),
    ("Bạn có muốn ăn không?", False, "Vietnamese", "sina wile ala wile moku"),
    ("Hôm nay trời rất đẹp", False, "Vietnamese", "tenpo suno ni la ma li pona"),
]

# Terminal punctuation, paired. The old model was reported to translate worse
# without it; each of these runs twice, bare and punctuated, so the two answers
# from one model can be compared to each other as well as across models.
PUNCT_PAIRS = [
    ("jan li moku e kili", ".", True, "English", "someone eats fruit"),
    ("soweli lili li lape lon tomo", ".", True, "English",
     "a small animal sleeps in the house"),
    ("jan pona mi li kama tawa tomo mi lon tenpo pimeja", ".", True, "English",
     "my friend came to my house at night"),
    ("sina sona ala sona e toki pona", "?", True, "English", "do you know toki pona?"),
    ("tenpo suno ni la mi pilin pona mute", ".", True, "Russian",
     "сегодня мне очень хорошо"),
    ("mama mi li pali e moku pona lon tomo moku", ".", True, "Russian",
     "мой родитель готовит вкусную еду на кухне"),
    ("mi wile moku e telo", ".", True, "Vietnamese", "tôi muốn uống nước"),
    ("jan lili li lukin e waso lon sewi", ".", True, "Vietnamese",
     "đứa trẻ nhìn con chim trên trời"),
    ("The cat is sleeping on the table", ".", False, "English",
     "soweli li lape lon supa"),
    ("What are you doing", "?", False, "English", "sina pali e seme"),
    ("Кошка спит на столе", ".", False, "Russian", "soweli li lape lon supa"),
    ("Что ты делаешь", "?", False, "Russian", "sina pali e seme"),
    ("Con mèo đang ngủ trên bàn", ".", False, "Vietnamese", "soweli li lape lon supa"),
    ("Tôi yêu bạn", ".", False, "Vietnamese", "mi olin e sina"),
]


def run(model: Path, prompt: str, n_predict: int) -> str:
    prompt_file = Path("build/compare/prompt.txt")
    prompt_file.write_text(prompt, encoding="utf-8")
    proc = subprocess.run(
        [
            str(BIN), "-m", str(model), "-f", str(prompt_file),
            "--no-display-prompt", "--simple-io", "--no-warmup",
            "-n", str(n_predict), "-c", "2048", "--temp", "0", "--top-k", "1",
            "-ngl", "0", "--no-perf", "-lv", "0",
        ],
        capture_output=True, text=True, timeout=900,
    )
    if proc.returncode != 0:
        return f"<<ERROR rc={proc.returncode}>>"
    return proc.stdout.strip().replace("[end of text]", "").strip()


# The focused probes from the 1.1 round, kept so the same questions get the same
# answers across every version rather than being re-invented each time.
FOCUS = {
    "toki pona as a language name": [
        ("sina sona ala sona e toki pona", True, "English"),
        ("mi sona e toki pona", True, "English"),
        ("toki pona li pona tawa mi", True, "English"),
        ("mi kama sona e toki pona lon tenpo suno ni", True, "English"),
        ("mi sona e toki pona", True, "Russian"),
    ],
    "la": [
        ("ilo mi li pakala la mi ken ala toki tawa sina", True, "English"),
        ("mi wile lape la mi tawa tomo", True, "English"),
        ("tenpo pimeja la mi lape", True, "English"),
        ("mi pilin ike la mi moku ala", True, "English"),
        ("sina kama la mi pilin pona", True, "English"),
        ("sina moku ala la sina pilin ike", True, "English"),
        ("mi wile lape la mi tawa tomo", True, "Russian"),
    ],
    "jan": [
        ("jan li moku e kili", True, "English"),
        ("jan li tawa ma", True, "English"),
        ("jan wan li lon tomo", True, "English"),
        ("jan li pali e tomo", True, "English"),
        ("jan li tawa ma", True, "Russian"),
        ("jan li moku e kili", True, "Vietnamese"),
    ],
    "unmarked number and tense": [
        ("mi moku e kili", True, "English"),
        ("mi tawa tomo", True, "English"),
        ("mi moku ala", True, "English"),
        ("mi pali tan ni: mi wile moku", True, "English"),
        ("jan lili li lukin e waso lon sewi", True, "English"),
        ("mi lon tomo nanpa tu", True, "English"),
    ],
    "rarer particles": [
        ("tomo pi telo nasa li lon poka mi", True, "English"),
        ("jan pi ma ante li kama", True, "English"),
        ("sina o kama tawa mi", True, "English"),
        ("mi wile tawa taso mi ken ala", True, "English"),
        ("sina wile e telo anu moku", True, "English"),
        ("mi en sina li pali", True, "English"),
        ("mi kin wile moku", True, "English"),
    ],
}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", action="append", required=True,
                    help="label=path, repeatable")
    ap.add_argument("--n-predict", type=int, default=512)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    models = [(m.split("=", 1)[0], Path(m.split("=", 1)[1])) for m in args.model]

    plan = [("general", t, f, o, e, "") for (t, f, o, e) in CASES]
    for text, mark, from_tp, other, expected in PUNCT_PAIRS:
        plan.append(("punct", text, from_tp, other, expected, "bare"))
        plan.append(("punct", text + mark, from_tp, other, expected, "marked"))
    for group, cases in FOCUS.items():
        for text, from_tp, other in cases:
            plan.append((f"focus:{group}", text, from_tp, other, "", ""))

    results = []
    for i, (group, text, from_tp, other, expected, variant) in enumerate(plan, 1):
        direction = f"tok\u2192{other}" if from_tp else f"{other}\u2192tok"
        prompt = prompt_for(text, from_tp, other, "SourceTarget")
        row = {"n": i, "group": group, "variant": variant, "direction": direction,
               "source": text, "expected": expected, "answers": {}}
        tag = f"{group}/{variant}" if variant else group
        print(f"[{i:3}/{len(plan)}] {tag:34} {direction:18} {text!r}", file=sys.stderr)
        if expected:
            print(f"      expected: {expected}", file=sys.stderr)
        for label, path in models:
            out = run(path, prompt, args.n_predict)
            row["answers"][label] = out
            print(f"      {label:5} {out}", file=sys.stderr, flush=True)
        results.append(row)

    args.out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n==> {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
