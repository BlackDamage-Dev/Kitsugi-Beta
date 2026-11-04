#!/usr/bin/env python3
"""Conservative Kotlin control inventory; ignores comments/string delimiters."""
import collections
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / 'app/src/main/java/com/kitsugi/animelist/ui'
BUTTON = re.compile(r'\b(TextButton|OutlinedButton|FilledTonalButton|Button|KitsugiButton|KitsugiTonalButton|KitsugiUiverseGlowButton)\s*\(')
MASK = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')

def masked(text):
    return MASK.sub(lambda m: ''.join('\n' if c == '\n' else ' ' for c in m.group()), text)

def end_of(code, start, opening, closing):
    depth = 0
    for i in range(start, len(code)):
        if code[i] == opening: depth += 1
        elif code[i] == closing:
            depth -= 1
            if depth == 0: return i
    return None

def controls(text):
    code = masked(text)
    for match in BUTTON.finditer(code):
        end = end_of(code, match.end()-1, '(', ')')
        if end is None: continue
        start = end + 1
        while start < len(code) and code[start].isspace(): start += 1
        if start == len(code) or code[start] != '{': continue
        finish = end_of(code, start, '{', '}')
        if finish is None: continue
        body = text[start+1:finish]
        if re.search(r'\b(?:Icon|Image|AsyncImage|Canvas|CircularProgressIndicator)\s*\(', masked(body)): continue
        if 'KitsugiGlyphs.' in body or 'KitsugiActionMark(' in body: continue
        # Only simple label buttons; don't insert before a nested layout or control.
        if not re.match(r'\s*Text\s*\(', masked(body)): continue
        resource = re.search(r'R\.string\.(\w+)', body)
        literal = re.search(r'Text\s*\(\s*(?:text\s*=\s*)?"([^"\n]+)"', body)
        label = resource.group(1) if resource else literal.group(1) if literal else None
        yield start+1, finish, label

if __name__ == '__main__':
    counts = collections.Counter()
    for path in SRC.rglob('*.kt'):
        for _,_,label in controls(path.read_text()): counts[label] += 1
    for label, count in counts.most_common(): print(count, label)
