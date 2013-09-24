import * from whiley.lang.*
import * from whiley.gpgpu.*

public (int,int) psudorandom(int temp):
	temp = (34 * temp + 747) % 1000

	return (temp, temp)

public ([int], [int], [int], [int], [int], [int]) createNodes(int n):
	ax = []
	ay = []
	az = []
	avx = []
	avy = []
	avz = []

	temp = 112
	for i in 0..n:
		temp,x = psudorandom(temp)
		temp,y = psudorandom(temp)
		temp,z = psudorandom(temp)
		temp,vx = psudorandom(temp)
		temp,vy = psudorandom(temp)
		temp,vz = psudorandom(temp)

		ax = ax + [x]
		ay = ay + [y]
		az = az + [z]
		avx = avx + [vx]
		avy = avy + [vy]
		avz = avz + [vz]

	return (ax, ay, az, avx, avy, avz)

public void ::main(System.Console sys):
	n = 500

	tup = createNodes(n)

	x,y,z,vx,vy,vz = tup
	
	numRuns = 50
	numWarmRuns = 2
	for testNumber in 0..(numRuns+numWarmRuns):
		if numWarmRuns <= 0:
			beginGPUBenchmarking()

		x,y,z,vx,vy,vz = tup
		bx,by,bz,bvx,bvy,bvz = tup

		count = 0
		while count < 30:
			count = count + 1

			for this in 0..n:
				fx = 0
				fy = 0
				fz = 0

				tx = x[this]
				ty = y[this]
				tz = z[this]

				tvx = vx[this]
				tvy = vy[this]
				tvz = vz[this]

				for other in 0..n:
					if this != other:
						ox = x[other]
						oy = y[other]
						oz = z[other]

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

				bx[this] = tx
				by[this] = ty
				bz[this] = tz
				bvx[this] = tvx
				bvy[this] = tvy
				bvz[this] = tvz

			sys.out.println((bx,by,bz,bvx,bvy,bvz))

			tempx = x
			tempy = y
			tempz = z
			tempvx = vx
			tempvy = vy
			tempvz = vz

			x = bx
			y = by
			z = bz
			vx = bvx
			vy = bvy
			vz = bvz

			x = tempx
			y = tempy
			z = tempz
			vx = tempvx
			vy = tempvy
			vz = tempvz

			for i in 0..n:
				x[i] = Math.round(x[i])
				y[i] = Math.round(y[i])
				z[i] = Math.round(z[i])
				vx[i] = Math.round(vx[i])
				vy[i] = Math.round(vy[i])
				vz[i] = Math.round(vz[i])

		if numWarmRuns <= 0:
			endGPUBenchmarking()
		numWarmRuns = numWarmRuns - 1

	printOutGPUBenchmarkResults()

	sys.out.println((x,y,z,vx,vy,vz))