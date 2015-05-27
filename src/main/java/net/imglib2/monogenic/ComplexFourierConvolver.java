package net.imglib2.monogenic;

import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.fft2.FFT;
import net.imglib2.algorithm.fft2.FFTMethods;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class ComplexFourierConvolver< T extends ComplexType< T >>
{

	private final ImgFactory< T > factory;

	private final Img< T > sourceFourier;

	private final int numThreads;

	private final Dimensions paddedDims;

	public ComplexFourierConvolver( final RandomAccessibleInterval< T > source, final ImgFactory< T > factory, final int numThreads )
	{
		this.factory = factory;
		this.numThreads = numThreads;
		final long[] paddedDimensions = new long[ source.numDimensions() ];
		FFTMethods.dimensionsComplexToComplexFast( source, paddedDimensions );
		this.paddedDims = FinalDimensions.wrap( paddedDimensions );
		this.sourceFourier = forwardFFT( source );
	}

	public < R extends ComplexType< R >> void convolve( final RandomAccessibleInterval< R > kernelFourier, final Img< T > target, final boolean frequencyCentered )
	{
		if ( frequencyCentered )
		{
			/*
			 * Rearrange quadrants: we assume that the kernel FFT was given with
			 * the central frequency at the image center, instead of at 0,0.
			 */
			final IntervalView< R > padded = padZero( kernelFourier );
			copyTo( padded, target );
			QuadrantArranger.rearrangeFFTQuadrants( target, false, numThreads );
		}
		else
		{
			/*
			 * Rearrange quadrants: Here we have to re-arrange kernel first to
			 * make it frequency centered, then pad it, then re-arrange it
			 * again. Otherwise, we would be adding false 0 frequencies every
			 * where.
			 */
			QuadrantArranger.rearrangeFFTQuadrants( kernelFourier, false, numThreads );
			final IntervalView< R > padded = padZero( kernelFourier );
			copyTo( padded, target );
			QuadrantArranger.rearrangeFFTQuadrants( target, false, numThreads );
		}

		multiply( target, sourceFourier );
		FFT.complexToComplexInverse( target, numThreads );
	}

	public Img< T > createTarget( final Interval input )
	{
		final Interval paddingInterval = FFTMethods.paddingIntervalCentered( input, paddedDims );
		return factory.create( paddingInterval, sourceFourier.firstElement() );
	}

	/**
	 * Does <code>A = A * B</code>.
	 * 
	 * @param A
	 * @param B
	 */
	private final void multiply( final Img< T > A, final Img< T > B )
	{
		final Cursor< T > ca = A.localizingCursor();
		final RandomAccess< T > rb = B.randomAccess( A );

		while ( ca.hasNext() )
		{
			ca.fwd();
			rb.setPosition( ca );
			ca.get().mul( rb.get() );
		}
	}

	private final IntervalView< T > pad( final RandomAccessibleInterval< T > rai )
	{
		final ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T >> extended = Views.extendMirrorDouble( rai );
		final Interval paddingInterval = FFTMethods.paddingIntervalCentered( rai, paddedDims );
		final IntervalView< T > view = Views.interval( extended, paddingInterval );
		return view;
	}

	private final < R extends NumericType< R >> IntervalView< R > padZero( final RandomAccessibleInterval< R > rai )
	{
		final ExtendedRandomAccessibleInterval< R, RandomAccessibleInterval< R >> extended = Views.extendZero( rai );
		final Interval paddingInterval = FFTMethods.paddingIntervalCentered( rai, paddedDims );
		final IntervalView< R > view = Views.interval( extended, paddingInterval );
		return view;
	}

	private Img< T > forwardFFT( final RandomAccessibleInterval< T > source )
	{
		final IntervalView< T > sourceView = pad( source );
		final Img< T > target = copyToNonVirtual( sourceView );
		FFT.complexToComplexForward( target, numThreads );
		return target;
	}

	private final Img< T > copyToNonVirtual( final IntervalView< T > source )
	{
		final Img< T > img = factory.create( source, source.firstElement() );
		final IntervalView< T > offseted = Views.offsetInterval( source, source );

		final Cursor< T > cursor = img.localizingCursor();
		final RandomAccess< T > ra = offseted.randomAccess( img );
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			ra.setPosition( cursor );
			cursor.get().set( ra.get() );
		}
		return img;
	}
	
	private static final < R extends ComplexType< R >, T extends ComplexType< T >> void copyTo( final IntervalView< R > source, final Img< T > dest )
	{
		final IntervalView< R > view = Views.offsetInterval( source, source );
		final Cursor< R > cursor = view.localizingCursor();
		final RandomAccess< T > ra = dest.randomAccess( source );
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			ra.setPosition( cursor );
			ra.get().setComplexNumber( cursor.get().getRealDouble(), cursor.get().getImaginaryDouble() );
		}
	}
}
