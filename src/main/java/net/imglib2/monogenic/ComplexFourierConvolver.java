package net.imglib2.monogenic;

import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.fft2.FFT;
import net.imglib2.algorithm.fft2.FFTMethods;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class ComplexFourierConvolver< T extends ComplexType< T >>
{

	private final ImgFactory< T > factory;

	private final Img< T > sourceFourier;

	private final int numThreads;

	private final Dimensions paddedDims;

	private final Dimensions sourceDims;

	public ComplexFourierConvolver( final RandomAccessibleInterval< T > source, final ImgFactory< T > factory, final int numThreads )
	{
		final long[] dims = new long[ source.numDimensions() ];
		source.dimensions( dims );
		this.sourceDims = new FinalDimensions( dims );
		this.factory = factory;
		this.numThreads = numThreads;
		final long[] paddedDimensions = new long[ source.numDimensions() ];
		FFTMethods.dimensionsComplexToComplexFast( source, paddedDimensions );
		this.paddedDims = FinalDimensions.wrap( paddedDimensions );
		this.sourceFourier = forwardFFT(source);
	}

	public Img< T > convolve( final RandomAccessibleInterval< T > kernelFourier )
	{
		final IntervalView< T > padded = pad( kernelFourier );
		final Img< T > target = copyToNonVirtual( padded );
		multiply( target, sourceFourier );
		FFT.complexToComplexInverse( target, numThreads );
		return crop( target );
	}

	private Img< T > crop( final Img< T > target )
	{
		final Img< T > crop = factory.create( sourceDims, target.firstElement() );
		final long[] min = new long[ target.numDimensions() ];
		final long[] max = new long[ target.numDimensions() ];
		for ( int d = 0; d < max.length; d++ )
		{
			min[ d ] = ( target.dimension( d ) - crop.dimension( d ) ) / 2;
			max[ d ] = crop.dimension( d ) + min[ d ];
		}
		final FinalInterval cropInterval = new FinalInterval( min, max );
		final IntervalView< T > offsetInterval = Views.offsetInterval( target, cropInterval );

		final Cursor< T > cursor = crop.localizingCursor();
		final RandomAccess< T > ra = offsetInterval.randomAccess( crop );
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			ra.setPosition( cursor );
			cursor.get().set( ra.get() );
		}

		return crop;
	}

	private final void multiply( final RandomAccessibleInterval< T > A, final Img< T > B )
	{
		final RandomAccess< T > ra = A.randomAccess( B );
		final Cursor< T > cursor = B.localizingCursor();
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			ra.setPosition( cursor );
			ra.get().mul( cursor.get() );
		}
	}

	private final IntervalView< T > pad( final RandomAccessibleInterval< T > rai )
	{
		final ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T >> extended = Views.extendMirrorDouble( rai );
		final Interval paddingInterval = FFTMethods.paddingIntervalCentered( rai, paddedDims );
		final IntervalView< T > view = Views.interval( extended, paddingInterval );
		return view;
	}

	private Img< T > forwardFFT( final RandomAccessibleInterval< T > source )
	{
		final IntervalView< T > sourceView = pad( source );
		final Img< T > target = copyToNonVirtual( sourceView);
		FFT.complexToComplexForward( target, numThreads );
		return target;
	}


	private final Img< T > copyToNonVirtual( final IntervalView< T > source)
	{
		final Img< T > img = factory.create( source, source.firstElement() );
		final IntervalView< T > offseted = Views.offsetInterval( source, source );

		final Cursor< T > cursor = img.localizingCursor();
		final RandomAccess< T > ra = offseted.randomAccess( img );
		while(cursor.hasNext()) {
			cursor.fwd();
			ra.setPosition( cursor );
			cursor.get().set( ra.get() );
		}
		return img;
	}

}
