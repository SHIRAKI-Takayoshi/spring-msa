#!/bin/sh
docker run --rm -i peterevans/vegeta sh -c "echo 'GET http://172.20.32.72:5000/api/account' | vegeta attack -rate=10 -duration=180s | tee results.bin | vegeta report -type='hist[0,5ms, 10ms, 15ms, 20ms]'"
