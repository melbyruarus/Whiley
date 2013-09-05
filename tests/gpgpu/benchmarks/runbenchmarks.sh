#!/bin/bash

SAVEDIR=$1.results

if [ $# -ne 1 ]
then
	echo "Expecting save directory argument"
	exit 1
fi

rm *.{cl,class,wyil,wyasm} 2>/dev/null

mkdir $SAVEDIR

wyb n-body && \
wyb gaussian_blur && \
wyb gameoflife && \
wyb mandelbrot_float && \
wyb mandelbrot_int && \
wyb matrix_multiply && #\
#wyb reduce_sum &&

mv *.csv $SAVEDIR/ &&

rm *.{cl,class,wyil,wyasm} 2>/dev/null