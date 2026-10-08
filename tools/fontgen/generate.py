#!/usr/bin/env python3
"""Generate FontWidths.java: per-codepoint advance widths for the Minecraft
Java default font (vanilla font pixels).

Pipeline replicates the vanilla client (verified against decompiled 1.21.11):
  1. Load font/default.json, resolving "reference" providers recursively.
     Provider filters are evaluated against default options
     (uniform=false, jp=false); a provider is active only if every filter
     entry matches.
  2. Providers are applied in order; the FIRST provider that supplies a
     codepoint wins (FontSet#computeGlyphInfo iterates and breaks).
  3. bitmap provider:
       cell   = image / grid size
       scale  = declared height / cell pixel height
       glyphW = rightmost non-empty pixel column in cell + 1 (0 if empty);
                a pixel is non-empty when any of r,g,b,a != 0
       advance = int(0.5 + glyphW * scale) + 1
  4. space provider: explicit advances from JSON.
  5. unihex provider (GNU Unifont .hex inside a zip):
       rows are left-aligned into a 32-bit mask;
       no override  -> left = leadingZeros(mask), right = 31 - trailingZeros(mask)
                       (empty mask -> left=0, right=bitWidth, as in vanilla)
       override     -> left/right taken from size_overrides, bits outside discarded
       width   = right - left + 1
       advance = width // 2 + 1            (oversample 2.0)
  6. Codepoints not covered by anything fall back to DEFAULT_ADVANCE (9),
     '\n' is 0.

Usage:
  python3 generate.py --output <FontWidths.java>
  python3 generate.py --assets <assets_dir> --pack <pack_dir> --output <FontWidths.java>

Paths default to the repo .env file:
  VANILLA_ASSETS_PATH=...   dir with the vanilla font assets
  RESOURCE_PACK_PATH=...    resource pack root with custom glyphs (optional)

<assets_dir> must contain:
  font/default.json (+ referenced json files), textures/font/*.png,
  font/include/unifont.json and font/unifont.zip downloaded from the Mojang
  asset index (in recent versions these are not shipped inside client.jar).

<pack_dir> (optional) is a resource pack root (the folder with pack.mcmeta).
Its assets/minecraft/font/default.json providers are prepended before the
vanilla ones, so pack glyphs override vanilla exactly like the client merges
them (first provider in order wins).

Only Python stdlib is required (PNGs are decoded manually).
"""

import argparse
import json
import struct
import sys
import zlib
from pathlib import Path

BMP_MAX = 0xFFFF
DEFAULT_OPTIONS = {"uniform": False, "jp": False}
DEFAULT_ADVANCE = 9


class AssetError(Exception):
    pass


def split_location(loc):
    """'minecraft:font/x.png' -> ('minecraft', 'font/x.png')"""
    ns, sep, path = loc.partition(":")
    return (ns, path) if sep else (None, loc)


def find_asset(roots, loc, kind):
    """Resolves 'namespace:path' against the asset roots (pack first, vanilla
    last, like the client's pack stack). Tolerates three root layouts:
      <root>/assets/<ns>/<path>   (extracted client assets / pack root)
      <root>/<ns>/<path>          (an assets/ dir itself)
      <root>/<path>               (the <ns> namespace dir itself)
    kind: 'json' | 'texture' | 'raw'."""
    ns, path = split_location(loc)
    ns = ns or "minecraft"
    if kind == "texture" and not path.startswith("textures/"):
        path = "textures/" + path
    if kind == "json" and not path.endswith(".json"):
        path += ".json"
    # Font json ids ('minecraft:include/space') resolve relative to font/,
    # so for json every layout also gets a font/-prefixed variant.
    rels = ["assets/%s/%s" % (ns, path), "%s/%s" % (ns, path), path]
    if kind == "json" and not path.startswith("font/"):
        rels += ["assets/%s/font/%s" % (ns, path), "%s/font/%s" % (ns, path), "font/%s" % path]
    for root in roots:
        for rel in rels:
            cand = root / rel
            if cand.is_file():
                return cand
    return None


def root_font_json(root):
    """The root's own assets/minecraft/font/default.json, whatever the layout."""
    for rel in ("assets/minecraft/font/default.json", "minecraft/font/default.json",
                "font/default.json"):
        cand = root / rel
        if cand.is_file():
            return cand
    return None


def resolve_location(loc):
    return split_location(loc)[1]


# ---------------------------------------------------------------------------
# Minimal PNG decoder (8-bit depth, color types 0/2/3/4/6)
# ---------------------------------------------------------------------------

def decode_png(path):
    with open(path, "rb") as fh:
        data = fh.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssetError("%s: not a PNG file" % path)
    pos = 8
    ihdr = None
    palette = None
    trns = None
    idat = bytearray()
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        ctype = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            ihdr = struct.unpack(">IIBBBBB", chunk)
        elif ctype == b"PLTE":
            palette = chunk
        elif ctype == b"tRNS":
            trns = chunk
        elif ctype == b"IDAT":
            idat.extend(chunk)
        elif ctype == b"IEND":
            break
    if ihdr is None:
        raise AssetError("%s: missing IHDR" % path)
    width, height, depth, color, _, _, interlace = ihdr
    if interlace != 0:
        raise AssetError("%s: interlaced PNG not supported" % path)
    if depth not in (1, 2, 4, 8):
        raise AssetError("%s: unsupported bit depth %d" % (path, depth))
    if interlace != 0:
        raise AssetError("%s: interlaced PNG not supported" % path)

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    raw = zlib.decompress(bytes(idat))
    stride = (width * channels * depth + 7) // 8
    pixels = bytearray(width * height * 4)
    prev = bytearray(stride)
    src = 0
    for y in range(height):
        filt = raw[src]
        src += 1
        line = bytearray(raw[src:src + stride])
        src += stride
        if filt == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        elif filt != 0:
            raise AssetError("%s: bad filter %d" % (path, filt))
        prev = line

        def sample(i, line=line, depth=depth):
            bit_pos = i * depth
            byte = line[bit_pos >> 3]
            shift = 8 - depth - (bit_pos & 7)
            return (byte >> shift) & ((1 << depth) - 1)

        base = y * width * 4
        if color == 6:
            for x in range(width):
                o = base + x * 4
                pixels[o] = sample(x * 4)
                pixels[o + 1] = sample(x * 4 + 1)
                pixels[o + 2] = sample(x * 4 + 2)
                pixels[o + 3] = sample(x * 4 + 3)
        elif color == 2:
            for x in range(width):
                o = base + x * 4
                pixels[o] = sample(x * 3)
                pixels[o + 1] = sample(x * 3 + 1)
                pixels[o + 2] = sample(x * 3 + 2)
                pixels[o + 3] = 255
        elif color == 4:
            for x in range(width):
                o = base + x * 4
                v = sample(x * 2) * 255 // ((1 << depth) - 1)
                pixels[o] = pixels[o + 1] = pixels[o + 2] = v
                pixels[o + 3] = sample(x * 2 + 1) * 255 // ((1 << depth) - 1)
        elif color == 0:
            for x in range(width):
                o = base + x * 4
                v = sample(x) * 255 // ((1 << depth) - 1)
                pixels[o] = pixels[o + 1] = pixels[o + 2] = v
                pixels[o + 3] = 255
        else:
            if palette is None:
                raise AssetError("%s: palette chunk missing" % path)
            for x in range(width):
                idx = sample(x)
                o = base + x * 4
                r, g, b = palette[idx * 3:idx * 3 + 3]
                a = trns[idx] if (trns is not None and idx < len(trns)) else 255
                pixels[o] = r
                pixels[o + 1] = g
                pixels[o + 2] = b
                pixels[o + 3] = a
    return width, height, pixels


def pixel_nonempty(pixels, width, x, y):
    o = (y * width + x) * 4
    return pixels[o + 3] != 0


# ---------------------------------------------------------------------------
# Providers
# ---------------------------------------------------------------------------

def load_font_json_at(roots, path, seen):
    """Loads one concrete font json file; 'reference' providers resolve
    against every root (pack first, vanilla last)."""
    if path is None or not path.is_file():
        return []
    key = str(path)
    if key in seen:
        return []
    seen.add(key)
    with open(path, encoding="utf-8") as fh:
        doc = json.load(fh)
    out = []
    for prov in doc.get("providers", []):
        if prov.get("type") == "reference":
            out.extend(load_font_json_at(roots, find_asset(roots, prov["id"], "json"), seen))
        else:
            out.append(prov)
    return out


def load_font_json(roots, rel, seen=None):
    return load_font_json_at(roots, find_asset(roots, rel, "json"), seen or set())


def provider_active(prov):
    for opt, want in (prov.get("filter") or {}).items():
        if DEFAULT_OPTIONS.get(opt, False) != want:
            return False
    return True


def to_codepoints(s):
    """JSON strings may carry UTF-16 surrogate pairs; combine them like
    String#codePoints() does."""
    units = [ord(c) for c in s]
    out = []
    i = 0
    while i < len(units):
        u = units[i]
        if 0xD800 <= u <= 0xDBFF and i + 1 < len(units) and 0xDC00 <= units[i + 1] <= 0xDFFF:
            out.append(0x10000 + ((u - 0xD800) << 10) + (units[i + 1] - 0xDC00))
            i += 2
        else:
            out.append(u)
            i += 1
    return out


def bitmap_advances(roots, prov, stats):
    img_path = find_asset(roots, prov["file"], "texture")
    if img_path is None:
        raise AssetError("bitmap texture not found: %s" % prov["file"])
    img_w, img_h, px = decode_png(img_path)
    rows_raw = prov["chars"]
    n_rows = len(rows_raw)
    n_cols = len(to_codepoints(rows_raw[0]))
    cell_w = img_w // n_cols
    cell_h = img_h // n_rows
    declared_h = prov.get("height", 8)
    scale = declared_h / cell_h
    out = {}
    dup = 0
    for r, row_str in enumerate(rows_raw):
        cps = to_codepoints(row_str)
        for c, cp in enumerate(cps):
            if cp == 0 or cp > BMP_MAX:
                continue
            x0 = c * cell_w
            y0 = r * cell_h
            gw = 0
            for dx in range(cell_w - 1, -1, -1):
                hit = False
                for dy in range(cell_h):
                    if pixel_nonempty(px, img_w, x0 + dx, y0 + dy):
                        hit = True
                        break
                if hit:
                    gw = dx + 1
                    break
            adv = int(0.5 + gw * scale) + 1
            if cp in out:
                dup += 1
            out[cp] = adv
    stats["bitmap_dup_%s" % resolve_location(prov["file"])] = dup
    return out


def space_advances(prov):
    out = {}
    for key, val in prov.get("advances", {}).items():
        for cp in map(ord, key):
            out[cp] = int(val)
    return out


def unihex_advances(roots, prov):
    import zipfile
    zpath = find_asset(roots, prov["hex_file"], "raw")
    if zpath is None:
        raise AssetError("unihex zip not found: %s" % prov["hex_file"])
    glyphs = {}
    with zipfile.ZipFile(zpath) as zf:
        for name in zf.namelist():
            if not name.endswith(".hex"):
                continue
            with zf.open(name) as fh:
                for raw in fh:
                    line = raw.strip()
                    if not line:
                        continue
                    head, _, body = line.partition(b":")
                    cp = int(head, 16)
                    n = len(body)
                    if n not in (32, 64, 96, 128):
                        raise AssetError("%s:%d bad hex row length %d" % (name, cp, n))
                    bit_w = n // 32 * 8
                    digits_per_row = bit_w // 4
                    mask = 0
                    shift = 32 - bit_w
                    for row in range(16):
                        seg = body[row * digits_per_row:(row + 1) * digits_per_row]
                        mask |= (int(seg, 16) << shift) & 0xFFFFFFFF
                    glyphs[cp] = (mask, bit_w)
    overrides = [(_parse_codepoint(ov["from"]), _parse_codepoint(ov["to"]), ov["left"], ov["right"])
                 for ov in prov.get("size_overrides", [])]
    out = {}
    for cp, (mask, bit_w) in glyphs.items():
        if cp > BMP_MAX:
            continue
        left = right = None
        for start, end, ol, orr in overrides:
            if start <= cp <= end:
                left, right = ol, orr
                break
        if left is None:
            if mask == 0:
                left, right = 0, bit_w
            else:
                lz = 0
                for b in range(31, -1, -1):
                    if (mask >> b) & 1:
                        lz = 31 - b
                        break
                tz = 0
                for b in range(32):
                    if (mask >> b) & 1:
                        tz = b
                        break
                left, right = lz, 31 - tz
        out[cp] = (right - left + 1) // 2 + 1
    return out


def _parse_codepoint(s):
    cps = list(map(ord, s))
    if len(cps) == 2:
        hi, lo = cps
        return 0x10000 + ((hi - 0xD800) << 10) + (lo - 0xDC00)
    return cps[0]


# ---------------------------------------------------------------------------
# Assembly + Java emission
# ---------------------------------------------------------------------------

def collect(roots):
    """roots[0] is the highest-priority pack (optional), the last one is the
    vanilla assets. Each root contributes its own assets/minecraft/font/
    default.json, in root order; the first provider supplying a codepoint
    wins — same as the client's pack stack."""
    stats = {}
    providers = []
    seen = set()
    for root_idx, root in enumerate(roots):
        for prov in load_font_json_at(roots, root_font_json(root), seen):
            providers.append((root_idx, prov))
    ordered = [(ri, p) for ri, p in providers if provider_active(p)]
    stats["active_providers"] = [p["type"] for _, p in ordered]
    advances = {}
    sources = {}
    origin = {}
    for root_idx, prov in ordered:
        ptype = prov["type"]
        if ptype == "bitmap":
            part = bitmap_advances(roots, prov, stats)
        elif ptype == "space":
            part = space_advances(prov)
        elif ptype == "unihex":
            part = unihex_advances(roots, prov)
        elif ptype == "reference":
            raise AssetError("nested reference provider")
        else:
            continue
        fresh = 0
        for cp, adv in part.items():
            if cp not in advances:
                advances[cp] = adv
                sources[cp] = ptype
                origin[cp] = root_idx
                fresh += 1
        stats["covered_%s" % ptype] = stats.get("covered_%s" % ptype, 0) + fresh
    return advances, sources, origin, stats


def escape_java(cp):
    ch = chr(cp)
    if 0x20 <= cp <= 0x7E:
        if ch in ('"', "\\"):
            return "\\" + ch
        return ch
    return "\\u%04x" % cp


def emit_java(advances, sources, version, out_path, regenerate_cmd, pack_name):
    buckets = {}
    for cp, adv in advances.items():
        if cp > BMP_MAX:
            continue
        if sources.get(cp) not in ("space", "bitmap"):
            continue
        buckets.setdefault(adv, []).append(cp)
    bucket_cps = set()
    for cps in buckets.values():
        bucket_cps.update(cps)
        cps.sort()

    ranges = []
    run_start = None
    run_adv = None
    for cp in range(BMP_MAX + 1):
        adv = DEFAULT_ADVANCE if cp not in advances else advances[cp]
        if cp in bucket_cps:
            adv = None
        if adv != run_adv:
            if run_adv is not None:
                ranges.append((run_start, cp - 1, run_adv))
            run_start = cp
            run_adv = adv
    if run_adv is not None:
        ranges.append((run_start, BMP_MAX, run_adv))

    lines = []
    w = lines.append
    w("package dev.horuse.pixelay.content;")
    w("")
    w("import java.util.Arrays;")
    w("")
    w("/**")
    w(" * Generated by tools/fontgen/generate.py for Minecraft Java %s%s." % (version, " + resource pack " + pack_name if pack_name else ""))
    w(" * Regenerate (from the repo root):")
    w(" *   %s" % regenerate_cmd)
    w(" */")
    w("public final class FontWidths {")
    w("\tprivate static final int TABLE_SIZE = 0x10000;")
    w("\tprivate static final int DEFAULT_ADVANCE = %d;" % DEFAULT_ADVANCE)
    w("")
    for adv in sorted(buckets):
        tokens = [escape_java(cp) for cp in buckets[adv]]
        parts = []
        cur = []
        cur_len = 0
        for tok in tokens:
            if cur_len + len(tok) > 200 and cur:
                parts.append("".join(cur))
                cur = []
                cur_len = 0
            cur.append(tok)
            cur_len += len(tok)
        if cur:
            parts.append("".join(cur))
        w('\tprivate static final String W%d =' % adv)
        for idx, part in enumerate(parts):
            tail = "" if idx == len(parts) - 1 else " +"
            w('\t\t\t"%s"%s' % (part, tail))
        w("\t\t\t;")
    w("")
    flat = []
    for start, end, adv in ranges:
        flat.extend((start, end, adv))
    chunk_size = 6000
    chunks = [flat[i:i + chunk_size] for i in range(0, len(flat), chunk_size)]
    for idx, chunk in enumerate(chunks):
        w("\tprivate static int[] ranges%d() {" % idx)
        w("\t\treturn new int[] {")
        step = 12
        for i in range(0, len(chunk), step):
            group = ", ".join(str(v) for v in chunk[i:i + step])
            comma = "," if i + step < len(chunk) else ""
            w("\t\t\t%s%s" % (group, comma))
        w("\t\t};")
        w("\t}")
        w("")
    w("\tprivate static final int[] ADVANCES = new int[TABLE_SIZE];")
    w("")
    w("\tstatic {")
    w("\t\tArrays.fill(ADVANCES, DEFAULT_ADVANCE);")
    for idx in range(len(chunks)):
        w("\t\tapplyRanges(ranges%d());" % idx)
    for adv in sorted(buckets):
        w("\t\tapply(W%d, %d);" % (adv, adv))
    w("\t\tADVANCES['\\n'] = 0;")
    w("\t}")
    w("")
    w("\tprivate FontWidths() {")
    w("\t}")
    w("")
    w("\tprivate static void applyRanges(int[] ranges) {")
    w("\t\tfor (int i = 0; i < ranges.length; i += 3) {")
    w("\t\t\tfor (int cp = ranges[i], max = ranges[i + 1]; cp <= max; cp++) {")
    w("\t\t\t\tADVANCES[cp] = ranges[i + 2];")
    w("\t\t\t}")
    w("\t\t}")
    w("\t}")
    w("")
    w("\tprivate static void apply(String chars, int advance) {")
    w("\t\tfor (int i = 0; i < chars.length(); i++) {")
    w("\t\t\tADVANCES[chars.charAt(i)] = advance;")
    w("\t\t}")
    w("\t}")
    w("")
    w("\tpublic static int advance(int codePoint) {")
    w("\t\tif (codePoint == '\\n') {")
    w("\t\t\treturn 0;")
    w("\t\t}")
    w("\t\tif (codePoint < 0 || codePoint >= TABLE_SIZE) {")
    w("\t\t\treturn DEFAULT_ADVANCE;")
    w("\t\t}")
    w("\t\treturn ADVANCES[codePoint];")
    w("\t}")
    w("}")

    with open(out_path, "w", encoding="ascii", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")

    covered = sum(len(v) for v in buckets.values())
    uni = sum(1 for cp, s in sources.items() if cp <= BMP_MAX and s == "unihex")
    print("bucket codepoints : %d (space/bitmap exact)" % covered)
    print("unihex codepoints : %d (BMP)" % uni)
    print("ranges emitted    : %d" % len(ranges))
    print("total defined     : %d / %d BMP codepoints" % (covered + uni, BMP_MAX + 1))


def read_env():
    """Parses the repo .env (repo root = two levels up) into a dict."""
    env = {}
    path = Path(__file__).resolve().parents[2] / ".env"
    if not path.is_file():
        return env
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        env[key.strip()] = val.strip().strip('"').strip("'")
    return env


def resolve_repo_rel(value):
    """Relative .env paths are resolved against the repo root."""
    path = Path(value).expanduser()
    return path if path.is_absolute() else Path(__file__).resolve().parents[2] / path


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--assets", default=None, help="dir with font/*.json, textures/font/*.png, font/unifont.zip (default: .env VANILLA_ASSETS_PATH)")
    ap.add_argument("--pack", default=None, help="resource pack root (default: .env RESOURCE_PACK_PATH)")
    ap.add_argument("--output", required=True, help="path of FontWidths.java to write")
    ap.add_argument("--version", default="1.21.11", help="MC version recorded in javadoc")
    args = ap.parse_args(argv)

    env = read_env()
    if args.assets:
        assets = resolve_repo_rel(args.assets)
    elif env.get("VANILLA_ASSETS_PATH"):
        assets = resolve_repo_rel(env["VANILLA_ASSETS_PATH"])
    else:
        ap.error("--assets or .env VANILLA_ASSETS_PATH is required")
    if not assets.is_dir():
        ap.error("assets dir not found: %s" % assets)

    pack = args.pack or env.get("RESOURCE_PACK_PATH")
    roots = [assets]
    pack_name = None
    pack_path = None
    if pack:
        pack_path = resolve_repo_rel(pack)
        if not pack_path.is_dir():
            print("# RESOURCE_PACK_PATH does not exist, skipping: %s" % pack_path)
        else:
            roots.insert(0, pack_path)
            pack_name = pack_path.name

    advances, sources, origin, stats = collect(roots)
    for k, v in stats.items():
        print("# %s: %s" % (k, v))

    # Guard: if these are wrong, the vanilla providers were not loaded.
    for ch, expected in (("!", 2), ("W", 6), ("0", 6), (" ", 4), ("А", 6)):
        got = advances.get(ord(ch))
        if got != expected:
            raise AssetError("sanity check failed: '%s' advance %s, expected %d "
                             "(vanilla assets missing?)" % (ch, got, expected))

    if pack_name:
        print("# pack glyph advances (%s):" % pack_name)
        for cp in sorted(c for c, ri in origin.items() if ri == 0):
            print("#   U+%04X '%s' -> %d" % (cp, chr(cp), advances[cp]))

    cmd = "python3 tools/fontgen/generate.py --output %s" % args.output
    emit_java(advances, sources, args.version, args.output, cmd, pack_name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
