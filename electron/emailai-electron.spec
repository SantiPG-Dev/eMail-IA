Name:           emailai-electron
Version:        1.0.0
Release:        1%{?dist}
Summary:        eMail-IA Desktop App (Electron wrapper)
License:        Proprietary
URL:            https://github.com/santipg/eMail-IA
Vendor:         Santiago Pérez Gómez <SantiPG-Dev@gmx.com>
Packager:       Santiago Pérez Gómez <SantiPG-Dev@gmx.com>
BuildArch:      x86_64
AutoReqProv:    no

# Runtime deps (Electron 33 on Linux). Mirror electron-builder/fpm defaults.
Requires:       gtk3
Requires:       libnotify
Requires:       nss
Requires:       libXScrnSaver
Requires:       libXtst
Requires:       alsa-lib
Requires:       at-spi2-core
Requires:       xdg-utils

%description
eMail-IA Desktop App — Electron wrapper around the eMail-IA Java backend.
Bundled: Electron 33 runtime, app.asar, backend.jar.
Requires Java 21 on PATH at runtime (JRE not bundled).

# binary-only package. %install copies the prebuilt linux-unpacked dir
# straight into buildroot — bypasses the fpm 1.9.3 builddir/BUILDROOT nesting bug
# that makes rpmbuild report every file as "File not found".
%install
SRC="%{emailai_src}"
ICON="%{emailai_icon}"
install -d -m 0755 %{buildroot}/opt/eMail-IA
cp -a "$SRC"/. %{buildroot}/opt/eMail-IA/
chmod 4755 %{buildroot}/opt/eMail-IA/chrome-sandbox

install -d -m 0755 %{buildroot}%{_datadir}/applications
cat > %{buildroot}%{_datadir}/applications/emailai-electron.desktop <<'EOF'
[Desktop Entry]
Name=eMail-IA
Exec=/opt/eMail-IA/emailai-electron %U
Terminal=false
Type=Application
Icon=emailai-electron
StartupWMClass=eMail-IA
Comment=eMail-IA Desktop App (Electron wrapper)
Categories=Office;
EOF

install -d -m 0755 %{buildroot}%{_datadir}/icons/hicolor/256x256/apps
install -m 0644 "$ICON" %{buildroot}%{_datadir}/icons/hicolor/256x256/apps/emailai-electron.png

%files
/opt/eMail-IA
%{_datadir}/applications/emailai-electron.desktop
%{_datadir}/icons/hicolor/256x256/apps/emailai-electron.png

%changelog
* Mon Jul 27 2026 Santiago Pérez Gómez <SantiPG-Dev@gmx.com> - 1.0.0-1
- Initial RPM build (bypasses fpm 1.9.3 builddir bug via hand-written spec).
