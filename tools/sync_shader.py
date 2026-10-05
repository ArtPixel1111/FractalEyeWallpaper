#!/usr/bin/env python3
"""Копирует GLSL из assets Android-проекта в веб-превью fractal-eye.html.

Источник истины — файлы в app/src/main/assets:
    quad.vert   -> const VS в fractal-eye.html
    shader.frag -> const FS в fractal-eye.html

Использование:
    python3 tools/sync_shader.py          # обновить fractal-eye.html
    python3 tools/sync_shader.py --check  # только проверить, код возврата 1 при расхождении
"""
import pathlib
import re
import sys

ASSETS = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets"
HTML = ASSETS / "fractal-eye.html"
BLOCKS = {"VS": "quad.vert", "FS": "shader.frag"}


def js_block(name: str, source: str) -> str:
    # Исходник попадает в шаблонную строку JS: эти символы её сломают.
    for bad in ("`", "${", "\\"):
        if bad in source:
            sys.exit(f"{BLOCKS[name]}: недопустимая последовательность {bad!r} для JS-шаблона")
    if not source.isascii():
        sys.exit(f"{BLOCKS[name]}: только ASCII (часть GLSL-компиляторов не принимает иное)")
    return f"const {name}=`\n{source}`;\n"


def sync(html: str) -> str:
    for name, filename in BLOCKS.items():
        source = (ASSETS / filename).read_text(encoding="utf-8")
        pattern = re.compile(
            rf"(// <shader:{name}:begin>\n).*?(// <shader:{name}:end>)", re.DOTALL)
        if not pattern.search(html):
            sys.exit(f"В {HTML.name} нет маркеров // <shader:{name}:begin> … :end>")
        html = pattern.sub(lambda m: m.group(1) + js_block(name, source) + m.group(2), html)
    return html


def main() -> None:
    current = HTML.read_text(encoding="utf-8")
    updated = sync(current)
    if "--check" in sys.argv:
        if updated != current:
            sys.exit("fractal-eye.html расходится с шейдерами: запусти tools/sync_shader.py")
        print("OK: fractal-eye.html совпадает с шейдерами")
        return
    if updated != current:
        HTML.write_text(updated, encoding="utf-8")
        print("fractal-eye.html обновлён")
    else:
        print("Без изменений")


if __name__ == "__main__":
    main()
