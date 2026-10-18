# 랜딩 사진과 영상

랜딩(`web/src/features/landing`)이 쓰는 그림이다. 모두 CC0(퍼블릭 도메인 기증)이라 출처 표기 의무가 없어서 화면에는 출처를 적지 않고, 이 파일에 원본과 작가를 남긴다.

## 사진 (`photos/`)

원본 사진을 숲색 망점으로 바꿔 투명 바탕 WebP로 저장했다. 화면에서는 세이지 면 위에 `mix-blend-mode: multiply`로 얹는다.

| 파일               | 원본                                                                                                                           | 작가     | 라이선스 |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------ | -------- | -------- |
| `tent-cone.webp`   | [White tent set grass snowy](https://www.rawpixel.com/image/3286320/free-photo-image-camp-camping-cc0)                         | rawpixel | CC0      |
| `boots.webp`       | [Boots of a man](https://commons.wikimedia.org/wiki/File:Boots_of_a_man.jpg)                                                   | W.carter | CC0      |
| `stove.webp`       | [A camping stove and a gas canister](https://wordpress.org/photos/photo/275695cdef/)                                           | Anis     | CC0      |
| `tent-valley.webp` | [White camping tent mountain](https://www.rawpixel.com/image/3285625/free-photo-image-travel-camping-cc0)                      | rawpixel | CC0      |
| `meadow.webp`      | [male backpacker lying meadow mountains](https://www.rawpixel.com/image/3293447/free-photo-image-camping-cc0-creative-commons) | rawpixel | CC0      |
| `tent-dome.webp`   | [August 2009, Tent in Daisetsuzan](https://commons.wikimedia.org/wiki/File:August_2009,_Tent_in_Daisetsuzan.jpg)               | Syced    | CC0      |

망점 처리 값(같은 사진을 다시 만들거나 새 사진을 더할 때 맞춘다):

- 원본을 가로 1400px로 줄이고, 밝기 분포의 최솟값~최댓값으로 대비를 맞춘다.
- 6px 간격 격자에 점을 찍고, 줄마다 반 칸씩 엇갈린다.
- 점 반지름 = 6px × 0.56 × v. v = ((1 − 밝기) − 0.12) / 0.88을 감마 1.6으로 올린 값이다. 어두운 사진(`tent-valley`)은 감마 2.6, `tent-dome`은 1.1로 둔다.
- 카드에 얹는 사진(`tent-cone`, `boots`, `stove`, `meadow`)은 가로 62%·세로 55% 지점을 중심으로 타원 모양으로 가장자리를 흐린다.
- 점 색은 `rgb(52 98 70)`, 바탕은 투명. WebP 품질 82, 알파 품질 90.

## 영상 (`video/`)

| 파일                                    | 원본                                                                                  | 작가          | 라이선스 |
| --------------------------------------- | ------------------------------------------------------------------------------------- | ------------- | -------- |
| `campfire.webm`, `campfire-poster.webp` | [Fire آتش 07](https://commons.wikimedia.org/wiki/File:Fire_%D8%A2%D8%AA%D8%B4_07.ogv) | Mostafameraji | CC0      |

Wikimedia Commons가 만든 480p WebM을 그대로 쓰고, 화면에서 흑백으로 바꿔 숲색 마무리 면 위에 `multiply`로 깐다. 동작 줄이기를 켜면 보이지 않는다.
