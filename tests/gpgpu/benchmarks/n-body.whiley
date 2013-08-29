import * from whiley.lang.*
import * from whiley.gpgpu.*

public (int,int) psudorandom(int temp):
	temp = (34 * temp + 747) % 1000

	return (temp, temp)

public [int] createNodes(int n):
	data = []

	temp = 112
	for i in 0..n:
		temp,x = psudorandom(temp)
		temp,y = psudorandom(temp)
		temp,z = psudorandom(temp)
		temp,vx = psudorandom(temp)
		temp,vy = psudorandom(temp)
		temp,vz = psudorandom(temp)

		data = data + [x, y, z, vx/10, vy/10, vz/10]

	return data

public void ::main(System.Console sys):
	n = 500

	indata = []

	data = createNodes(n)
	
	numRuns = 50
	numWarmRuns = 2
	for testNumber in 0..(numRuns+numWarmRuns):
		if numWarmRuns <= 0:
			beginGPUBenchmarking()

		indata = data
		outdata = data

		count = 0
		while count < 30:
			count = count + 1

			for this in 0..n:
				fx = 0
				fy = 0
				fz = 0

				tx = indata[this * 6 + 0]
				ty = indata[this * 6 + 1]
				tz = indata[this * 6 + 2]

				tvx = indata[this * 6 + 3]
				tvy = indata[this * 6 + 4]
				tvz = indata[this * 6 + 5]

				for other in 0..n:
					if this != other:
						ox = indata[other * 6 + 0]
						oy = indata[other * 6 + 1]
						oz = indata[other * 6 + 2]

						dx = ox - tx
						dy = oy - ty
						dz = oz - tz

						ofx = 1000
						if dx > 0:
							ofx = ofx / dx
						ofy = 1000
						if dy > 0:
							ofy = ofy / dy
						ofz = 1000
						if dz > 0:
							ofz = ofz / dz

						fx = fx + ofx
						fy = fy + ofy
						fz = fz + ofz

				tvx = tvx + fx
				tvy = tvy + fy
				tvz = tvz + fz

				tx = tx + tvx
				ty = ty + tvy
				tz = tz + tvz

				outdata[this * 6 + 0] = tx
				outdata[this * 6 + 1] = ty
				outdata[this * 6 + 2] = tz
				outdata[this * 6 + 3] = tvx
				outdata[this * 6 + 4] = tvy
				outdata[this * 6 + 5] = tvz

			temp = outdata
			outdata = indata
			indata = temp

			for i in 0..|indata|:
				indata[i] = Math.round(indata[i])

		if numWarmRuns <= 0:
			endGPUBenchmarking()
		numWarmRuns = numWarmRuns - 1

	printOutGPUBenchmarkResults()

	sys.out.println(indata)