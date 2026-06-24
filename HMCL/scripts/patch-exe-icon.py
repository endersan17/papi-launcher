#!/usr/bin/env python3
"""Patch HMCLauncher.exe with custom icon from icon.png.

Usage:
    ./patch-exe-icon.py <input_exe> <output_exe> <icon_png>

Requires: imagemagick, pngquant (optional), python3 with pefile
"""

import struct
import os
import subprocess
import sys
import tempfile


def main():
    if len(sys.argv) != 4:
        print("Usage: patch-exe-icon.py <input_exe> <output_exe> <icon_png>")
        sys.exit(1)

    input_exe = sys.argv[1]
    output_exe = sys.argv[2]
    icon_png = sys.argv[3]

    if not os.path.exists(input_exe):
        print(f"ERROR: Input exe not found: {input_exe}")
        sys.exit(1)
    if not os.path.exists(icon_png):
        print(f"ERROR: Icon PNG not found: {icon_png}")
        sys.exit(1)

    print(f"Patching {input_exe} -> {output_exe} with icon from {icon_png}")

    # Generate properly sized PNGs
    sizes = [24, 32, 48, 256]
    png_files = {}
    has_magick = (
        subprocess.run(["which", "magick"], capture_output=True).returncode == 0
    )
    has_pngquant = (
        subprocess.run(["which", "pngquant"], capture_output=True).returncode == 0
    )

    with tempfile.TemporaryDirectory(prefix="papi-icon-") as tmpdir:
        for s in sizes:
            out = os.path.join(tmpdir, f"{s}x{s}.png")
            if has_magick:
                subprocess.run(
                    [
                        "magick",
                        icon_png,
                        "-resize",
                        f"{s}x{s}",
                        "-background",
                        "none",
                        "-gravity",
                        "center",
                        "-extent",
                        f"{s}x{s}",
                        out,
                    ],
                    check=True,
                    capture_output=True,
                )

                if has_pngquant:
                    qout = os.path.join(tmpdir, f"{s}x{s}_q.png")
                    subprocess.run(
                        [
                            "pngquant",
                            "--force",
                            "--quality=40-60",
                            "--output",
                            qout,
                            out,
                        ],
                        capture_output=True,
                    )
                    if os.path.exists(qout) and os.path.getsize(qout) > 0:
                        png_files[s] = qout
                    else:
                        png_files[s] = out
                else:
                    png_files[s] = out
            else:
                print("WARNING: ImageMagick not found, cannot generate icons")
                sys.exit(1)

        # Read original exe
        import pefile

        pe = pefile.PE(input_exe)

        # Get resource file offsets (latest pefile API uses arrays)
        resource_dir = None
        for entry in pe.DIRECTORY_ENTRY_RESOURCE.entries:
            if entry.id == 3:  # RT_ICON
                resource_dir = entry.directory
            elif entry.id == 14:  # RT_GROUP_ICON
                group_dir = entry.directory

        if not resource_dir:
            print("ERROR: No RT_ICON resources found")
            sys.exit(1)

        # Map icon IDs (1-4) to their file offsets and sizes
        icon_map = {}
        for entry in resource_dir.entries:
            icon_id = entry.id
            if hasattr(entry, "directory"):
                for lang_entry in entry.directory.entries:
                    if hasattr(lang_entry, "data"):
                        rva = lang_entry.data.struct.OffsetToData
                        size = lang_entry.data.struct.Size
                        # Convert RVA to file offset
                        for section in pe.sections:
                            if (
                                section.VirtualAddress
                                <= rva
                                < section.VirtualAddress + section.Misc_VirtualSize
                            ):
                                file_off = (
                                    rva
                                    - section.VirtualAddress
                                    + section.PointerToRawData
                                )
                                icon_map[icon_id] = (file_off, size)
                                break

        if len(icon_map) != 4:
            print(f"WARNING: Found {len(icon_map)} icon resources, expected 4")

        # Read exe bytes for patching
        with open(input_exe, "rb") as f:
            exe_bytes = bytearray(f.read())

        # Expected size mapping: (icon_id, expected_px)
        icon_sizes = [(1, 24), (2, 32), (3, 48), (4, 256)]

        # Replace each icon
        for icon_id, px in icon_sizes:
            if icon_id not in icon_map:
                print(f"  WARNING: Icon ID {icon_id} not found, skipping")
                continue

            file_off, old_size = icon_map[icon_id]
            png_data = open(png_files[px], "rb").read()
            png_size = len(png_data)

            if png_size > old_size:
                print(
                    f"  WARNING: Icon {px}x{px} ({png_size}b) > slot ({old_size}b), trying lower quality"
                )
                qout = os.path.join(tmpdir, f"{px}x{px}_q2.png")
                subprocess.run(
                    [
                        "pngquant",
                        "--force",
                        "--quality=10-30",
                        "--output",
                        qout,
                        png_files[px],
                    ],
                    capture_output=True,
                )
                if os.path.exists(qout) and os.path.getsize(qout) <= old_size:
                    png_data = open(qout, "rb").read()
                    png_size = len(png_data)
                else:
                    print(
                        f"  ERROR: Still too large ({png_size}b > {old_size}b). Install pngquant for better compression."
                    )
                    sys.exit(1)

            padded = png_data + b"\x00" * (old_size - png_size)
            exe_bytes[file_off : file_off + old_size] = padded
            print(
                f"  Icon {icon_id} ({px}x{px}): {png_size}b written to slot {old_size}b"
            )

        # Update group icon resource
        # Build new group icon header
        new_group = struct.pack("<HHH", 0, 1, 4)
        for icon_id, px in icon_sizes:
            if icon_id in icon_map:
                _, _ = icon_map[icon_id]
                png_data = open(png_files[px], "rb").read()
                png_size = len(png_data)
                w = px if px < 256 else 0
                h = px if px < 256 else 0
                new_group += struct.pack(
                    "<BBBBHHIH", w, h, 0, 0, 1, 32, png_size, icon_id
                )

        # Find and update group icon
        if group_dir:
            for entry in group_dir.entries:
                if hasattr(entry, "directory"):
                    for lang_entry in entry.directory.entries:
                        if hasattr(lang_entry, "data"):
                            rva = lang_entry.data.struct.OffsetToData
                            size = lang_entry.data.struct.Size
                            for section in pe.sections:
                                if (
                                    section.VirtualAddress
                                    <= rva
                                    < section.VirtualAddress + section.Misc_VirtualSize
                                ):
                                    g_off = (
                                        rva
                                        - section.VirtualAddress
                                        + section.PointerToRawData
                                    )
                                    new_group_padded = new_group + b"\x00" * (
                                        size - len(new_group)
                                    )
                                    exe_bytes[g_off : g_off + size] = new_group_padded
                                    print(
                                        f"  Group icon updated ({len(new_group)} bytes)"
                                    )
                                    break

        with open(output_exe, "wb") as f:
            f.write(exe_bytes)

        print(f"Done: {output_exe} ({os.path.getsize(output_exe)} bytes)")


if __name__ == "__main__":
    main()
