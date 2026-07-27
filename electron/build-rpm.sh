#!/usr/bin/env bash
# Build the eMail-IA RPM via a hand-written spec.
# Bypasses electron-builder's bundled fpm 1.9.3 (broken on modern rpmbuild).
# Requires: linux-unpacked/ (produced by `electron-builder --dir` or `dist:linux`).
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d release/linux-unpacked ]; then
  echo ">> linux-unpacked missing, running electron-builder --dir…"
  npx tsc && npx electron-builder --dir
fi

mkdir -p release/rpm/{BUILD,RPMS,SOURCES,SPECS,SRPMS}

rpmbuild -bb \
  --define "_topdir $(pwd)/release/rpm" \
  --define "emailai_src $(pwd)/release/linux-unpacked" \
  --define "emailai_icon $(pwd)/assets/icon-256.png" \
  emailai-electron.spec

echo "✓ RPM → release/rpm/RPMS/x86_64/"
ls -lh release/rpm/RPMS/x86_64/*.rpm
