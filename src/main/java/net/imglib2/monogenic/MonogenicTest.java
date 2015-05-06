package net.imglib2.monogenic;

import ij.ImageJ;
import ij.ImagePlus;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.Cursor;
import net.imglib2.FinalDimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.fft2.FFT;
import net.imglib2.algorithm.fft2.FFTMethods;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class MonogenicTest
{
	public static < T extends RealType< T > & NativeType< T >> void main( String[] args )
	{
		/*
		 * Open ImageJ.
		 */

		ImageJ.main( args );
		final ImagePlus imp = new ImagePlus( "samples/Line.tif" );
		imp.show();
		final ImagePlus kernelImp = new ImagePlus( "samples/Kernel.tif" );
		kernelImp.show();

		/*
		 * Wrap to ImgLib2.
		 */

		final Img< T > source = ImageJFunctions.wrap( imp );
		final Img< T > kernel = ImageJFunctions.wrap( kernelImp );

		/*
		 * Copy to complex image.
		 */

		final Img< ComplexFloatType > kc = copyToComplex( source );
		final Img< ComplexFloatType > sc = copyToComplex( kernel );

		/*
		 * Extend.
		 */

		final ExtendedRandomAccessibleInterval< ComplexFloatType, Img< ComplexFloatType >> extendedSource = Views.extendMirrorDouble( sc );
		final ExtendedRandomAccessibleInterval< ComplexFloatType, Img< ComplexFloatType >> extendedKernel = Views.extendMirrorDouble( kc );

		/*
		 * FFT forward C to C.
		 */

		final long[] paddedDimensions = new long[ sc.numDimensions() ];
		FFTMethods.dimensionsComplexToComplexSmall( sc, paddedDimensions );
		final Interval paddingInterval = FFTMethods.paddingIntervalCentered( sc, FinalDimensions.wrap( paddedDimensions ) );

		final ExtendedRandomAccessibleInterval< ComplexFloatType, Img< ComplexFloatType >> extendedSC = Views.extendMirrorDouble( sc );
		final IntervalView< ComplexFloatType > sourceView = Views.interval( extendedSC, paddingInterval );
		FFT.complexToComplexForward( sourceView );

		System.out.println( Util.printInterval( sourceView ) );
		{
			final List< Img< FloatType >> ls = split( sourceView, new ArrayImgFactory< FloatType >() );
			ImageJFunctions.show( ls.get( 0 ), "Real fwd" );
			ImageJFunctions.show( ls.get( 1 ), "Imaginary fwd" );
		}

		/*
		 * FFT backward in complex.
		 */

		FFT.complexToComplexInverse( sourceView );
		final Interval unpaddingInterval = FFTMethods.unpaddingIntervalCentered( sourceView, sc );
		final IntervalView< ComplexFloatType > backView = Views.interval( sourceView, unpaddingInterval );

		System.out.println( Util.printInterval( backView ) );
		{
			final List< Img< FloatType >> split2 = split( backView, new ArrayImgFactory< FloatType >() );
			ImageJFunctions.show( split2.get( 0 ), "Real bck" );
			ImageJFunctions.show( split2.get( 1 ), "Imaginary bck" );
		}

	}

	private static final List< Img< FloatType >> split( IntervalView< ComplexFloatType > source, ImgFactory< FloatType > factory )
	{
		final Img< FloatType > real = factory.create( source, new FloatType() );
		final Img< FloatType > imaginary = factory.create( source, new FloatType() );

		final IntervalView< ComplexFloatType > offseted = Views.offsetInterval( source, source );
		final Cursor< ComplexFloatType > cs = offseted.cursor();
		final Cursor< FloatType > cr = real.cursor();
		final Cursor< FloatType > ci = imaginary.cursor();
		while ( cs.hasNext() )
		{
			cs.fwd();
			ci.fwd();
			cr.fwd();
			cr.get().setReal( cs.get().getRealFloat() );
			ci.get().setReal( cs.get().getImaginaryFloat() );
		}

		final List< Img< FloatType >> out = new ArrayList< Img< FloatType > >( 2 );
		out.add( real );
		out.add( imaginary );
		return out;
	}

	private static final List< Img< FloatType >> split( Img< ComplexFloatType > source )
	{
		final ImgFactory< FloatType > factory;
		try
		{
			factory = source.factory().imgFactory( new FloatType() );
		}
		catch ( final IncompatibleTypeException e )
		{
			throw new RuntimeException( "Could not instantiate complex img factory." );
		}

		final Img< FloatType > real = factory.create( source, new FloatType() );
		final Img< FloatType > imaginary = factory.create( source, new FloatType() );

		final Cursor< ComplexFloatType > cs = source.cursor();
		final RandomAccess< FloatType > rr = real.randomAccess( source );
		final RandomAccess< FloatType > ri = imaginary.randomAccess( source );
		while ( cs.hasNext() )
		{
			cs.fwd();
			ri.setPosition( cs );
			rr.setPosition( cs );
			rr.get().setReal( cs.get().getRealFloat() );
			ri.get().setReal( cs.get().getImaginaryFloat() );
		}

		final List< Img< FloatType >> out = new ArrayList< Img< FloatType > >( 2 );
		out.add( real );
		out.add( imaginary );
		return out;
	}

	private static final < T extends RealType< T >> Img< ComplexFloatType > copyToComplex( Img< T > source )
	{
		final ImgFactory< ComplexFloatType > imgFactory;
		try
		{
			imgFactory = source.factory().imgFactory( new ComplexFloatType() );
		}
		catch ( final IncompatibleTypeException e )
		{
			throw new RuntimeException( "Could not instantiate complex img factory." );
		}
		final Img< ComplexFloatType > img = imgFactory.create( source, new ComplexFloatType() );
		final Cursor< T > cs = source.cursor();
		final RandomAccess< ComplexFloatType > ri = img.randomAccess( source );
		while ( cs.hasNext() )
		{
			cs.fwd();
			ri.setPosition( cs );
			ri.get().setReal( cs.get().getRealFloat() );
			ri.get().setImaginary( 0f );
		}
		return img;
	}

}
