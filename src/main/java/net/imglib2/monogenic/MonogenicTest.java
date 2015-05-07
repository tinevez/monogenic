package net.imglib2.monogenic;

import ij.ImageJ;
import ij.ImagePlus;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;

public class MonogenicTest
{
	public static < T extends RealType< T > & NativeType< T >> void main( final String[] args )
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

		final Img< ComplexFloatType > sc = copyToComplex( source );
		final Img< ComplexFloatType > kc = copyToComplex( kernel );

		/*
		 * Convolve.
		 */
		
		final ImgFactory< ComplexFloatType > factory = new ArrayImgFactory< ComplexFloatType >();
		final int numThreads = 1;
		final ComplexFourierConvolver< ComplexFloatType > convolver = new ComplexFourierConvolver< ComplexFloatType >( sc, factory, numThreads );

		final Img< ComplexFloatType > convolved = convolver.convolve( kc );
		final List< Img< FloatType >> split = split( convolved );
		ImageJFunctions.show( split.get( 0 ), "Real" );
		ImageJFunctions.show( split.get( 1 ), "Imag" );
	}

	private static final List< Img< FloatType >> split( final Img< ComplexFloatType > source )
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

	private static final < T extends RealType< T >> Img< ComplexFloatType > copyToComplex( final Img< T > source )
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
