#!/usr/bin/env bash
#
# Prints the release notes for a tag as Markdown: every change since the previous
# semver tag, grouped by the pull request it came in through, with commits pushed
# straight to main listed on their own. Built from git alone, so it can be run
# locally to preview a release before tagging it.
#
# Commit subjects here are written as sentences for a reader, which is what makes a
# list of them a changelog. Merge commits carry the PR title on their body line.
#
# Usage: scripts/release-notes.sh <tag> [github-repo-url]
#        scripts/release-notes.sh HEAD      # preview what the next tag would say

set -euo pipefail

TAG="${1:?usage: release-notes.sh <tag> [github-repo-url]}"
REPO_URL="${2:-https://github.com/NetherQuartz/ilo-toki-app}"
SEMVER='[0-9]*.[0-9]*.[0-9]*'

PREV="$(git describe --tags --abbrev=0 --match "$SEMVER" "$TAG^" 2>/dev/null || true)"
RANGE="${PREV:+$PREV..}$TAG"

# Commits a pull request brought in: reachable from its branch, not from main.
# Kept as "sha number" lines in a file rather than an associative array, which the
# bash 3.2 that ships with macOS does not have.
PR_OF="$(mktemp)"
trap 'rm -f "$PR_OF"' EXIT
pr_of() { awk -v sha="$1" '$1 == sha { print $2; exit }' "$PR_OF"; }
PR_ORDER=()
while IFS=' ' read -r merge first second; do
    [[ -n "${second:-}" ]] || continue
    number="$(git log -1 --format=%s "$merge" | sed -n 's/^Merge pull request #\([0-9]*\).*/\1/p')"
    [[ -n "$number" ]] || continue
    PR_ORDER+=("$merge:$number")
    for sha in $(git rev-list --no-merges "$first..$second"); do
        echo "$sha $number" >> "$PR_OF"
    done
done < <(git rev-list --merges --reverse --parents "$RANGE")

if [[ -n "$PREV" ]]; then
    echo "Changes since $PREV."
else
    echo "The first release."
fi

for entry in ${PR_ORDER[@]+"${PR_ORDER[@]}"}; do
    merge="${entry%%:*}"
    number="${entry##*:}"
    title="$(git log -1 --format=%b "$merge" | sed -n '/./{p;q;}')"
    echo
    echo "### ${title:-Pull request #$number} ([#$number]($REPO_URL/pull/$number))"
    echo
    for sha in $(git rev-list --no-merges --reverse "$RANGE"); do
        if [[ "$(pr_of "$sha")" == "$number" ]]; then
            echo "- $(git log -1 --format=%s "$sha")"
        fi
    done
done

direct=()
for sha in $(git rev-list --no-merges --reverse "$RANGE"); do
    if [[ -z "$(pr_of "$sha")" ]]; then
        direct+=("$sha")
    fi
done
if (( ${#direct[@]} > 0 )); then
    echo
    echo "### Straight to main"
    echo
    for sha in ${direct[@]+"${direct[@]}"}; do
        echo "- $(git log -1 --format=%s "$sha")"
    done
fi

if [[ -n "$PREV" ]]; then
    echo
    echo "**Full diff:** [$PREV…$TAG]($REPO_URL/compare/$PREV...$TAG)"
fi
