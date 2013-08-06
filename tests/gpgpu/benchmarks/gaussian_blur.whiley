import * from whiley.lang.*
import * from whiley.gpgpu.*

public [real] createRealArray(int size):
	result = []
	for x in 0..size:
		result = result + [(real)x]
	return result

public ([real], int) createBlurMask(real sigma):
    maskSize = Math.ceil(3.0*sigma)
    arraySize = (maskSize*2+1)*(maskSize*2+1)
    mask = createRealArray(arraySize)
    sum = 0.0
    for a in -maskSize..maskSize:
        for b in -maskSize..maskSize:
            temp = Math.exp(-((real)(a*a+b*b) / (2.0*sigma*sigma)))
            sum = sum + temp
            mask[a+maskSize+(b+maskSize)*(maskSize*2+1)] = temp

    for i in 0..arraySize:
        mask[i] = mask[i] / sum
 
    return (mask, maskSize)

public real sample([real] image, int x, int y, int size):
	if x >= 0 && x < size && y >= 0 && y < size:
		return image[x + y * size]
	else:
		return 0

public void ::debugImage(System.Console sys, [real] image, int size):
	x = 0
	while x < size:
		y = 0
		while y < size:
			sys.out.print(Math.round(image[y * size + x]))
			sys.out.print("\t")
			y = y + 1
		sys.out.println("")
		x = x + 1

public void ::main(System.Console sys):
	size = 200

	mask,maskSize = createBlurMask(size/12)
	image = createRealArray(size * size)
	blurredImage = createRealArray(size * size)

	numRuns = 50
	numWarmRuns = 2
	for testNumber in 0..(numRuns+numWarmRuns):
		if numWarmRuns <= 0:
			beginGPUBenchmarking()

		for pos in 0..(size * size):
			posx = pos % size
			posy = (pos - posx) / size

		    sum = 0.0
		    for a in -maskSize..maskSize:
		        for b in -maskSize..maskSize:
		            sum = sum + mask[a+maskSize+(b+maskSize)*(maskSize*2+1)] * sample(image, posx + a, posy + b, size)
		 
		    blurredImage[pos] = sum

		if numWarmRuns <= 0:
			endGPUBenchmarking()
		numWarmRuns = numWarmRuns - 1

	printOutGPUBenchmarkResults()

	hash = 0.0
	count = 0
	while count < |blurredImage|:
		hash = hash + blurredImage[count]
		count = count + 1

	sys.out.println(Math.round(hash))
