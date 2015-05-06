package net.imglib2.monogenic;

import net.imglib2.Cursor;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.fft2.FFT;
import net.imglib2.algorithm.fft2.FFTMethods;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class ComplexConvolver
{

	final public static void convolve(
			final RandomAccessible< ComplexFloatType > img,
			final Interval imgInterval,
			final RandomAccessible< ComplexFloatType > kernel,
			final Interval kernelInterval,
			final int numThreads )
	{
		final int numDimensions = imgInterval.numDimensions();

		// the image has to be extended at least by kernelDimensions/2-1 in each
		// dimension so that
		// the pixels outside of the interval are used for the convolution.
		final long[] newDimensions = new long[ numDimensions ];

		for ( int d = 0; d < numDimensions; ++d )
		{
			newDimensions[ d ] = ( int ) imgInterval.dimension( d ) + ( int ) kernelInterval.dimension( d ) - 1;
		}

		// compute the size of the complex-valued output and the required
		// padding
		// based on the prior extended input image
		final long[] paddedDimensions = new long[ numDimensions ];

		//		FFTMethods.dimensionsRealToComplexFast( FinalDimensions.wrap( newDimensions ), paddedDimensions, fftDimensions );
		FFTMethods.dimensionsComplexToComplexFast( FinalDimensions.wrap( newDimensions ), paddedDimensions );

		// compute the new interval for the input image
		final Interval imgConvolutionInterval = FFTMethods.paddingIntervalCentered( imgInterval, FinalDimensions.wrap( paddedDimensions ) );

		// compute the new interval for the kernel image
		final Interval kernelConvolutionInterval = FFTMethods.paddingIntervalCentered( kernelInterval, FinalDimensions.wrap( paddedDimensions ) );

		// compute where to place the final Interval for the kernel so that the
		// coordinate in the center
		// of the kernel is at position (0,0)
		final long[] min = new long[ numDimensions ];
		final long[] max = new long[ numDimensions ];

		for ( int d = 0; d < numDimensions; ++d )
		{
			min[ d ] = kernelInterval.min( d ) + kernelInterval.dimension( d ) / 2;
			max[ d ] = min[ d ] + kernelConvolutionInterval.dimension( d ) - 1;
		}

		// assemble the correct kernel (size of the input + extended periodic +
		// top left at center of input kernel)
		final IntervalView< ComplexFloatType > kernelInput = Views.interval( Views.extendPeriodic( Views.interval( kernel, kernelConvolutionInterval ) ), new FinalInterval( min, max ) );
		final IntervalView< ComplexFloatType > imgInput = Views.interval( img, imgConvolutionInterval );

		// compute the FFT's
		FFT.complexToComplexForward( imgInput, numThreads );
		FFT.complexToComplexForward( kernelInput, numThreads );

		// multiply in place
		multiplyComplex( imgInput, kernelInput );

		// inverse FFT in place
		FFT.complexToComplexInverse( imgInput, numThreads );
	}

	final private static void multiplyComplex( final IntervalView< ComplexFloatType > imgInput, final IntervalView< ComplexFloatType > kernelInput )
	{
		final Cursor< ComplexFloatType > cursorA = imgInput.cursor();
		final Cursor< ComplexFloatType > cursorB = kernelInput.cursor();

		while ( cursorA.hasNext() )
		{
			cursorA.next().mul( cursorB.next() );
		}
	}
}
