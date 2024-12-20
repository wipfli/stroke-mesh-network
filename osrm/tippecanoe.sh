#! /bin/bash
# tippecanoe -o data/output.pmtiles output2.json --layer output --force --maximum-zoom 13 --minimum-zoom 6

tippecanoe -o routing-roads-california/0.pmtiles output-0.json --layer output --force --drop-fraction-as-needed --maximum-zoom 13 --minimum-zoom 6

tippecanoe -o routing-roads-california/1e6.pmtiles output-1e6.json --layer output --force --maximum-zoom 13 --minimum-zoom 6 --drop-fraction-as-needed
tippecanoe -o routing-roads-california/5e6.pmtiles output-5e6.json --layer output --force --maximum-zoom 13 --minimum-zoom 6

tippecanoe -o routing-roads-california/1e7.pmtiles output-1e7.json --layer output --force --maximum-zoom 13 --minimum-zoom 0
tippecanoe -o routing-roads-california/5e7.pmtiles output-5e7.json --layer output --force --maximum-zoom 13 --minimum-zoom 0

tippecanoe -o routing-roads-california/1e8.pmtiles output-1e8.json --layer output --force --maximum-zoom 13 --minimum-zoom 0
tippecanoe -o routing-roads-california/5e8.pmtiles output-5e8.json --layer output --force --maximum-zoom 13 --minimum-zoom 0

tippecanoe -o routing-roads-california/1e9.pmtiles output-1e9.json --layer output --force --maximum-zoom 13 --minimum-zoom 0
tippecanoe -o routing-roads-california/5e9.pmtiles output-5e9.json --layer output --force --maximum-zoom 13 --minimum-zoom 0

tippecanoe -o routing-roads-california/1e10.pmtiles output-1e10.json --layer output --force --maximum-zoom 13 --minimum-zoom 0
