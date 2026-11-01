# Home leaderboard artwork

Five original UI assets were generated with the built-in image generation tool on 2026-11-02, inspired by the supplied orange achievement header reference. They contain no lettering: the title, player names, ranks and levels remain native Compose text. The crown/laurel header and gold/silver/bronze/slate row backgrounds are bundled locally in `app/src/main/res/drawable-nodpi/home_rank_*_v1.webp`.

Each generated PNG was inspected, cropped to its alpha bounds (FFmpeg `alphaextract,bbox=min_val=8`), scaled to width 960 with Lanczos, and encoded with libwebp quality 85, compression level 6, preserving alpha. Total packaged artwork is 79,504 bytes (under 80 KiB). No network download or server changes are required. Originals remain in the image generator output folder; the application references only these project-local WebP files.

The app reuses four row backgrounds according to actual rank; user-specific text/images are never baked into them. The title uses Shabnam over the header's empty center. The 13sp orange registration pill is centered at the panel bottom, with 3dp vertical internal padding and a 44dp minimum touch target extending above the visible pill. Parent bottom padding is 4dp. Existing leaderboard caching, probability, user rank summary, and click handling are unchanged.

Assets:

| Resource | Bytes |
| --- | ---: |
| home_rank_header_v1.webp | 29,800 |
| home_rank_gold_v1.webp | 14,844 |
| home_rank_silver_v1.webp | 12,468 |
| home_rank_bronze_v1.webp | 12,154 |
| home_rank_regular_v1.webp | 10,238 |
