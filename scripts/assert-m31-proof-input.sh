#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 /absolute/path/to/proof-resources" >&2
    exit 2
fi

fail() {
    echo "assert-m31-proof-input: $1" >&2
    exit 1
}

file_mode() {
    if stat -f '%Lp' "$1" >/dev/null 2>&1; then
        stat -f '%Lp' "$1"
    else
        stat -c '%a' "$1"
    fi
}

proof_resources=$1
case "$proof_resources" in
    /*) ;;
    *) fail "proof resources directory must be absolute" ;;
esac

[[ -d "$proof_resources" && ! -L "$proof_resources" ]] \
    || fail "proof resources directory must be an existing non-symlink directory"
[[ $(file_mode "$proof_resources") == "700" ]] \
    || fail "proof resources directory must have mode 0700"

raw_dir="$proof_resources/raw"
[[ -d "$raw_dir" && ! -L "$raw_dir" ]] \
    || fail "proof raw directory must be an existing non-symlink directory"

for ca_file in \
    "$raw_dir/authbound_verifier_root_ca.pem" \
    "$raw_dir/authbound_m31_network_ca.pem"; do
    [[ -f "$ca_file" && ! -L "$ca_file" ]] \
        || fail "proof CA must be an existing non-symlink regular file"
    [[ $(file_mode "$ca_file") == "600" ]] \
        || fail "proof CA must have mode 0600"
done

proof_network_config="$proof_resources/xml/authbound_m31_network_security_config.xml"
[[ -f "$proof_network_config" && ! -L "$proof_network_config" ]] \
    || fail "proof network configuration must be an existing non-symlink regular file"
[[ $(file_mode "$proof_network_config") == "600" ]] \
    || fail "proof network configuration must have mode 0600"

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
gradle_file="$repository_root/resources-logic/build.gradle.kts"
assembly_gradle_file="$repository_root/assembly-logic/build.gradle.kts"
assembly_manifest="$repository_root/assembly-logic/src/main/AndroidManifest.xml"
network_config="$repository_root/network-logic/src/demo/res/xml/network_security_config.xml"
ktor_client="$repository_root/core-logic/src/demo/java/eu/europa/ec/corelogic/config/ProvideKtorHttpClient.kt"

rg -q 'providers\.gradleProperty\("authboundM31ProofResourcesDir"\)' "$gradle_file" \
    || fail "Gradle proof-resources property is missing"
rg -Uq 'val proofResourcesPath = Path\.of\(rawPath\)\s+require\(proofResourcesPath\.isAbsolute\)\s+val proofResources = proofResourcesPath\.toFile\(\)' "$gradle_file" \
    || fail "Gradle must reject a relative proof-resources property"
rg -q 'Files\.isDirectory\(proofRaw, NOFOLLOW_LINKS\)' "$gradle_file" \
    || fail "Gradle must reject a symlinked proof raw directory"
rg -q 'Files\.isRegularFile\(proofNetworkConfig, NOFOLLOW_LINKS\)' "$gradle_file" \
    || fail "Gradle must reject a missing or symlinked proof network configuration"
rg -Uq 'sourceSets\.named\("demo"\)\s*\{\s*res\.srcDir\(proofResources\)' "$gradle_file" \
    || fail "proof resources must feed the demo resource source set"
[[ $(rg -c 'res\.srcDir\(proofResources\)' "$gradle_file") == "1" ]] \
    || fail "proof resources must feed only one source set"

! rg -q 'src\s*=\s*"user"' "$network_config" \
    || fail "demo network configuration must not trust user CAs"
rg -q '<certificates src="system" />' "$network_config" \
    || fail "demo network configuration must trust system CAs"
! rg -q '<certificates src="@raw/authbound_verifier_root_ca" />' "$network_config" \
    || fail "reader trust must not become ordinary demo TLS trust"
! rg -q 'authbound_m31_network_ca' "$network_config" \
    || fail "ordinary demo network trust must not include proof-only CAs"
! rg -q 'src\s*=\s*"user"' "$proof_network_config" \
    || fail "proof network configuration must not trust user CAs"
rg -q '<certificates src="system" />' "$proof_network_config" \
    || fail "proof network configuration must trust system CAs"
rg -q '<certificates src="@raw/authbound_m31_network_ca" />' "$proof_network_config" \
    || fail "proof network configuration must trust the generated network CA bundle"
! rg -q '<certificates src="@raw/authbound_verifier_root_ca" />' "$proof_network_config" \
    || fail "verifier reader trust must remain separate from proof network trust"
rg -q 'android:networkSecurityConfig="\$\{networkSecurityConfig\}"' "$assembly_manifest" \
    || fail "application manifest must select network trust through a placeholder"
rg -q 'gradleProperty\("authboundM31ProofResourcesDir"\)\.isPresent' "$assembly_gradle_file" \
    || fail "proof network trust must require the explicit proof-resources property"
rg -q '@xml/authbound_m31_network_security_config' "$assembly_gradle_file" \
    || fail "proof network trust must select only the generated proof configuration"
[[ ! -e "$repository_root/resources-logic/src/main/res/raw/authbound_m31_network_ca.pem" ]] \
    || fail "proof network CA must not be tracked"

! rg -q 'X509TrustManager|TrustManager|HostnameVerifier|sslManager|SSLContext|trustAllCerts' "$ktor_client" \
    || fail "demo Ktor client must use platform trust"
