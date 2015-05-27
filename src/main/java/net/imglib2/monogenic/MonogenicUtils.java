package net.imglib2.monogenic;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.RGBStackMerge;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class MonogenicUtils
{
	public static final < T extends ComplexType< T >> List< Img< FloatType >> split( final RandomAccessibleInterval< T > source )
	{
		final ArrayImgFactory< FloatType > factory = new ArrayImgFactory< FloatType >();
		final Img< FloatType > real = factory.create( source, new FloatType() );
		final Img< FloatType > imaginary = factory.create( source, new FloatType() );

		final IntervalView< T > view = Views.offsetInterval( source, source );
		final Cursor< T > cs = view.cursor();
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
	
	public static final < T extends ComplexType< T >> ImagePlus toImagePlus( final RandomAccessibleInterval< T > source, final String name )
	{
		final List< Img< FloatType >> split = split( source );
		final ImagePlus realImp = ImageJFunctions.wrap( split.get( 0 ), "Real" );
		final ImagePlus imagImp = ImageJFunctions.wrap( split.get( 1 ), "Imag" );
		final ImagePlus merged = RGBStackMerge.mergeChannels( new ImagePlus[] { realImp, imagImp }, false );
		merged.setTitle( name );
		return merged;
	}

	public static final < T extends ComplexType< T >> void writeToImagePlus( final RandomAccessibleInterval< T > source, final ImagePlus merged )
	{
		final ImageStack stack = merged.getStack();
		final ImageProcessor realIP = stack.getProcessor( 1 );
		final ImageProcessor imagIP = stack.getProcessor( 2 );
		final RandomAccess< T > ra = source.randomAccess( source );
		for ( int x = 0; x < merged.getWidth(); x++ )
		{
			for ( int y = 0; y < merged.getHeight(); y++ )
			{
				ra.setPosition( x, 0 );
				ra.setPosition( y, 1 );
				realIP.setf( x, y, ra.get().getRealFloat() );
				imagIP.setf( x, y, ra.get().getImaginaryFloat() );
			}
		}
	}

	private MonogenicUtils()
	{}
}
