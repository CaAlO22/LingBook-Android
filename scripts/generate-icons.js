const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const DENSITIES = {
  mdpi: { mipmap: 48, foreground: 108 },
  hdpi: { mipmap: 72, foreground: 162 },
  xhdpi: { mipmap: 96, foreground: 216 },
  xxhdpi: { mipmap: 144, foreground: 324 },
  xxxhdpi: { mipmap: 192, foreground: 432 },
};

const ROOT = path.resolve(__dirname, '..');

async function renderSvg(svgPath, size) {
  return sharp(svgPath).resize(size, size).png().toBuffer();
}

async function applyCircleMask(buffer, size) {
  // Create a circular mask: white circle on black background.
  const mask = Buffer.alloc(size * size * 4);
  const cx = size / 2;
  const cy = size / 2;
  const r = size / 2;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const idx = (y * size + x) * 4;
      const dist = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
      if (dist <= r) {
        mask[idx] = 255;
        mask[idx + 1] = 255;
        mask[idx + 2] = 255;
        mask[idx + 3] = 255;
      } else {
        mask[idx] = 0;
        mask[idx + 1] = 0;
        mask[idx + 2] = 0;
        mask[idx + 3] = 0;
      }
    }
  }
  const maskImg = sharp(mask, { raw: { width: size, height: size, channels: 4 } }).png();
  return sharp(buffer)
    .ensureAlpha()
    .composite([{ input: await maskImg.toBuffer(), blend: 'dest-in' }])
    .png()
    .toBuffer();
}

async function main() {
  const logoSvg = path.join(ROOT, 'design-assets/lingbook_logo.svg');
  const foregroundSvg = path.join(ROOT, 'design-assets/ic_launcher_foreground.svg');

  // Design asset full logo.
  const logoPng = path.join(ROOT, 'design-assets/lingbook_logo.png');
  await sharp(logoSvg).resize(1024, 1024).png().toFile(logoPng);
  console.log('generated', logoPng);

  for (const [density, sizes] of Object.entries(DENSITIES)) {
    // Foreground drawable.
    const foregroundDir = path.join(ROOT, `app/src/main/res/drawable-${density}`);
    fs.mkdirSync(foregroundDir, { recursive: true });
    const foregroundPng = path.join(foregroundDir, 'ic_launcher_foreground.png');
    await sharp(foregroundSvg).resize(sizes.foreground, sizes.foreground).png().toFile(foregroundPng);
    console.log('generated', foregroundPng);

    // Mipmap square.
    const mipmapDir = path.join(ROOT, `app/src/main/res/mipmap-${density}`);
    fs.mkdirSync(mipmapDir, { recursive: true });
    const mipmapPng = path.join(mipmapDir, 'ic_launcher.png');
    await sharp(logoSvg).resize(sizes.mipmap, sizes.mipmap).png().toFile(mipmapPng);
    console.log('generated', mipmapPng);

    // Mipmap round.
    const roundPng = path.join(mipmapDir, 'ic_launcher_round.png');
    const squareBuf = await renderSvg(logoSvg, sizes.mipmap);
    const roundBuf = await applyCircleMask(squareBuf, sizes.mipmap);
    await sharp(roundBuf).png().toFile(roundPng);
    console.log('generated', roundPng);
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
