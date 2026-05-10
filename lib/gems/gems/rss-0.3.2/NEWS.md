# News

## 0.3.2 - 2025-12-19

### Improvements

  * Updated documents.
    * GH-59
    * Patch by 1s22s1.

  * Added support for Ruby 4.0.0.
    * GH-62
    * Patch by Taketo Takashima.

### Thanks

  * 1s22s1
  * Taketo Takashima

## 0.3.1 - 2024-08-02

### Improvements

  * itunes: Add support for lowercased values (`full`, `trailer` and
    `bonus`) for `<itunes:episodeType>`.
    * GH-51
    * GH-53
    * Reported by Artem Alimov.
    * Patch by Andrew H Schwartz.

### Thanks

  * Artem Alimov
  * Andrew H Schwartz

## 0.3.0 - 2023-08-12

### Improvements

  * itunes: Added support for `<itunes:type>`.

    GH-16

    Patch by Luis Alfredo Lorenzo.

  * itunes: Added support for `<itunes:episode>` and `<itunes:season>`.

    GH-31

    Patch by Daniel-Ernest Luff.

  * itunes: Added support for `<itunes:title>`.

    GH-44

    Patch by Ryan Brunner.

  * itunes: Added support for `<itunes:episodeType>`.

    GH-45

    Reported by Tim Uckun.

### Thanks

  * Luis Alfredo Lorenzo

  * Daniel-Ernest Luff

  * Ryan Brunner

  * Tim Uckun

## 0.2.9 - 2020-02-19

### Improvements

  * Removed needless taint check with Ruby 2.7.
    [GitHub#7][Patch by Jeremy Evans]

  * Added support for `itunes:image` in `item` element.
    [GitHub#11][Patch by Ian McKenzie]

### Thanks

  * Jeremy Evans

  * Ian McKenzie

## 0.2.8 - 2019-01-24

### Improvements

  * Stopped passing needless blocks.

  * Added support for seconds only `<itunes:duration>`.
    [GitHub#4][Reported by Jarvis Johnson]
    [GitHub#5][Patch by Aitor García Rey]

### Thanks

  * Jarvis Johnson

  * Aitor García Rey
