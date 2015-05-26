package net.imglib2.monogenic;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.real.FloatType;

public class MonogenicUtils
{
	public static final < T extends ComplexType< T >> List< Img< FloatType >> split( final Img< T > source )
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

		final Cursor< T > cs = source.cursor();
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

	private MonogenicUtils()
	{}
}
