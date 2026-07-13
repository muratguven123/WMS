#!/usr/bin/env python3
"""Sunum hazırlığı: LibreTranslate için eksik Argos dil modellerini kurar.

İngilizce (en) pivot dili olduğundan her hedef dil için en<->X çiftini kurar.
LibreTranslate, doğrudan modeli olmayan çiftleri en üzerinden pivot ederek çevirir.
Zaten kurulu modeller atlanır; script idempotent'tir.
"""
import sys
import argostranslate.package as pkg

# Sunumda kullanılacak diller (en zaten pivot). tr/fr/de/ru genelde kurulu.
TARGET_LANGS = ["tr", "es", "fr", "de", "it", "pt", "ru", "ar", "zh", "ja", "ko", "hi", "nl", "pl"]
PIVOT = "en"

print("[argos] Paket index guncelleniyor...", flush=True)
pkg.update_package_index()
available = pkg.get_available_packages()
installed = pkg.get_installed_packages()

installed_pairs = {(p.from_code, p.to_code) for p in installed}
print(f"[argos] Kurulu model sayisi: {len(installed_pairs)}", flush=True)

# İstenen çiftler: her iki yönde en<->X
wanted = set()
for lang in TARGET_LANGS:
    if lang == PIVOT:
        continue
    wanted.add((PIVOT, lang))
    wanted.add((lang, PIVOT))

to_install = []
for frm, to in sorted(wanted):
    if (frm, to) in installed_pairs:
        print(f"[argos] atlandi (kurulu): {frm}->{to}", flush=True)
        continue
    match = next((p for p in available if p.from_code == frm and p.to_code == to), None)
    if match is None:
        print(f"[argos] UYARI: paket bulunamadi: {frm}->{to}", flush=True)
        continue
    to_install.append(match)

print(f"[argos] Indirilecek model sayisi: {len(to_install)}", flush=True)

failed = []
for i, p in enumerate(to_install, 1):
    tag = f"{p.from_code}->{p.to_code}"
    print(f"[argos] ({i}/{len(to_install)}) indiriliyor: {tag} ...", flush=True)
    try:
        path = p.download()
        pkg.install_from_path(path)
        print(f"[argos] ({i}/{len(to_install)}) kuruldu: {tag}", flush=True)
    except Exception as e:  # noqa: BLE001
        print(f"[argos] HATA {tag}: {e}", flush=True)
        failed.append(tag)

print(f"[argos] BITTI. kurulan={len(to_install)-len(failed)} basarisiz={len(failed)}", flush=True)
if failed:
    print("[argos] basarisiz ciftler: " + ", ".join(failed), flush=True)
    sys.exit(1)
print("[argos] TUM MODELLER HAZIR", flush=True)
