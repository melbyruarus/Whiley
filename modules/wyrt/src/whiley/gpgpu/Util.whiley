// Copyright (c) 2013, Melby Ruarus (melby@ecs.vuw.ac.nz)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//    * Redistributions of source code must retain the above copyright
//      notice, this list of conditions and the following disclaimer.
//    * Redistributions in binary form must reproduce the above copyright
//      notice, this list of conditions and the following disclaimer in the
//      documentation and/or other materials provided with the distribution.
//    * Neither the name of the <organization> nor the
//      names of its contributors may be used to endorse or promote products
//      derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL MELBY RUARUS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package whiley.gpgpu

// The result is (flatArray, sizeOfEachDimension)
public native ([any], [int]) flattenMultidimensionalArray([any] multiDArray, int numberOfDimensions):
// Note!!: this method modifies multiDArray by reference, and should only be called by automatically generated code
public native [any] unflattenMultidimensionalArray([any] flatArray, [any] multiDArray, int numberOfDimensions, [int] sizeOfEachDimension):
public native [any] executeWYGPUKernelOverArray(string moduleName, int kernelID, [any] arguments, [any] sourceList):
public native [any] executeWYGPUKernelOverRange(string moduleName, int kernelID, [any] arguments, int numberOfDimensions, [int] starts, [int] ends):
public native void ::beginGPUBenchmarking():
public native void ::endGPUBenchmarking():
public native void ::printOutGPUBenchmarkResults():