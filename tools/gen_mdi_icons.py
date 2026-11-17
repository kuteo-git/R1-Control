#!/usr/bin/env python3
"""Generate MdiIcons.kt from the Material Design Icons path data.

Home Assistant names every entity's icon with an `mdi:` name, so the dashboard can draw the exact
icon HA asks for. This reads the household's icon set (tools/mdi_names.txt, one kebab-case mdi name per
line) and the @mdi/js path constants, then emits the Kotlin registry the app resolves against.

Run: python3 tools/gen_mdi_icons.py
It downloads @mdi/js into a temp dir via `npm pack`, so it needs npm on PATH and network the first time.
Add a name to tools/mdi_names.txt and rerun to grow the set.
"""
import re
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
NAMES = REPO / "tools" / "mdi_names.txt"
OUT = REPO / "composeApp/src/commonMain/kotlin/com/r1control/app/ui/icons/MdiIcons.kt"
MDI_VERSION = "7.4.47"


def mdi_source() -> str:
    """The @mdi/js bundle text that holds every `mdiXxx = "<path>"` constant."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        subprocess.run(["npm", "pack", f"@mdi/js@{MDI_VERSION}"], cwd=tmp, check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        tarball = next(tmp.glob("*.tgz"))
        with tarfile.open(tarball) as tar:
            member = tar.extractfile("package/commonjs/mdi.js")
            return member.read().decode()


def to_const(name: str) -> str:
    return "".join(seg[:1].upper() + seg[1:] for seg in name.split("-"))


def main() -> int:
    names = sorted({x.strip() for x in NAMES.read_text().splitlines() if x.strip()})
    consts = dict(re.findall(r'mdi([A-Za-z0-9]+)\s*=\s*"([^"]+)"', mdi_source()))

    entries, missing = {}, []
    for n in names:
        path = consts.get(to_const(n))
        if path:
            entries[n] = path
        else:
            missing.append(n)

    lines = [
        "package com.r1control.app.ui.icons",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.graphics.SolidColor",
        "import androidx.compose.ui.graphics.vector.ImageVector",
        "import androidx.compose.ui.graphics.vector.addPathNodes",
        "import androidx.compose.ui.unit.dp",
        "",
        "/**",
        " * Material Design Icons for Home Assistant devices. Home Assistant names every entity's icon with an",
        " * `mdi:` name, so the dashboard draws the exact icon it asks for. Generated from @mdi/js "
        + f"(tools/gen_mdi_icons.py); {len(entries)} icons the household's entities and the app's fallbacks use.",
        " */",
        "object MdiIcons {",
        "    private val cache = HashMap<String, ImageVector>()",
        "",
        "    /** The icon for an `mdi:` name (prefix optional), or null when it is one not generated. */",
        "    fun byName(name: String): ImageVector? {",
        "        val key = name.removePrefix(\"mdi:\")",
        "        val path = paths[key] ?: return null",
        "        return cache.getOrPut(key) { vector(path) }",
        "    }",
        "",
        "    /** Whether an `mdi:` name (prefix optional) is one this set carries. */",
        "    fun has(name: String): Boolean = paths.containsKey(name.removePrefix(\"mdi:\"))",
        "",
        "    private fun vector(path: String): ImageVector =",
        "        ImageVector.Builder(\"mdi\", 24.dp, 24.dp, 24f, 24f).apply {",
        "            addPath(addPathNodes(path), fill = SolidColor(Color.Black))",
        "        }.build()",
        "",
        "    private val paths: Map<String, String> = mapOf(",
    ]
    for n in sorted(entries):
        lines.append(f'        "{n}" to "{entries[n]}",')
    lines.append("    )")
    lines.append("}")

    OUT.write_text("\n".join(lines) + "\n")
    print(f"wrote {OUT.relative_to(REPO)} with {len(entries)} icons")
    if missing:
        print(f"missing ({len(missing)}): {' '.join(missing)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
