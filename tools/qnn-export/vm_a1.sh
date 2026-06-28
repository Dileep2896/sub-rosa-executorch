#!/bin/bash
# Phase A1 (runs ON the GCP VM): system deps + clone ExecuTorch @ latest v1.3.x + submodules + venv.
# Logs to ~/a1.log. Does NOT need the QAIRT SDK yet (that's phase A2).
set -eux
exec > "$HOME/a1.log" 2>&1
echo "===== A1 START $(date) ====="

sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    git cmake ninja-build build-essential ccache \
    python3.10 python3.10-venv python3.10-dev python3-pip \
    wget zip unzip libssl-dev

# ExecuTorch QNN backend requires g++ >= 13 (Ubuntu 22.04 ships 11)
if ! dpkg -l | grep -q "g++-13"; then
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y g++-13 gcc-13 || {
    sudo add-apt-repository -y ppa:ubuntu-toolchain-r/test
    sudo apt-get update -y
    sudo apt-get install -y g++-13 gcc-13
  }
fi
sudo update-alternatives --install /usr/bin/g++ g++ /usr/bin/g++-13 130
sudo update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-13 130
g++ --version | head -1

# Clone ExecuTorch, pin to the latest v1.3.x tag (matches our runtime AAR 1.3.1)
cd "$HOME"
[ -d executorch ] || git clone https://github.com/pytorch/executorch.git
cd executorch
git fetch --tags --quiet
TAG=$(git tag | grep -E '^v1\.3' | sort -V | tail -1 || true)
if [ -n "$TAG" ]; then git checkout "$TAG"; else echo "NO_v1.3_TAG_USING_DEFAULT"; fi
echo "ET_TAG=${TAG:-default} REV=$(git rev-parse --short HEAD)"

git submodule sync
git submodule update --init --recursive

# Python venv (QNN-enabled install happens in A2, once the SDK is present)
python3.10 -m venv "$HOME/et-venv"
"$HOME/et-venv/bin/pip" install --upgrade pip setuptools wheel

echo "===== A1_DONE $(date) ====="
