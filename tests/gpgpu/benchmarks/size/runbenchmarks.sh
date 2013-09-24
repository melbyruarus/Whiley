#!/bin/bash

SAVEDIR=$1.results

if [ $# -ne 1 ]
then
	echo "Expecting save directory argument"
	exit 1
fi

rm *.{cl,class,wyil,wyasm} 2>/dev/null

mkdir $SAVEDIR

wyb gaussian_blur_100 && \
wyb gaussian_blur_133 && \
wyb gaussian_blur_167 && \
wyb gaussian_blur_200 && \
wyb gaussian_blur_233 && \
wyb gaussian_blur_267 && \
wyb gaussian_blur_300 && \

wyb mandelbrot_float_0016 && \
wyb mandelbrot_float_0032 && \
wyb mandelbrot_float_0064 && \
wyb mandelbrot_float_0128 && \
wyb mandelbrot_float_0256 && \
wyb mandelbrot_float_0512 && \
wyb mandelbrot_float_1024 && \

mv *.csv $SAVEDIR/ &&

rm *.{cl,class,wyil,wyasm} 2>/dev/null