docker run --rm -it vegeta -c "echo 'GET http://gw:5000/api/account' | vegeta attack -rate=10 -duration=10s | tee results.bin | vegeta report -type='hist[0,5ms, 20ms, 40ms, 60ms]'"
