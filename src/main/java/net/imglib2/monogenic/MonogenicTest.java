package net.imglib2.monogenic;

import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ShortProcessor;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

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

public class MonogenicTest
{
	public static < T extends RealType< T > & NativeType< T >> void main( final String[] args )
	{
		final int pixelVal = 100000;
		final double sigma = 5;

		/*
		 * Open ImageJ.
		 */

		ImageJ.main( args );
		final ImagePlus imp = new ImagePlus( "samples/Line.tif" );
		imp.show();
		final ShortProcessor kip = new ShortProcessor( 51, 51 );
		kip.set( 25, 25, pixelVal );
		kip.blurGaussian( sigma );
		final ImagePlus kernelImp = new ImagePlus( "Kernel", kip );
		kernelImp.resetDisplayRange();
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
//		final Img< ComplexFloatType > kc = copyToComplex( kernel );


		/*
		 * Convolve.
		 */
		
		final ImgFactory< ComplexFloatType > factory = new ArrayImgFactory< ComplexFloatType >();
		final int numThreads = 1;
		final ComplexFourierConvolver< ComplexFloatType > convolver = new ComplexFourierConvolver< ComplexFloatType >( sc, factory, numThreads );

		// Generate a suitable holder for results.
		final Img< ComplexFloatType > target = convolver.createTarget( kernel );
		convolver.convolve( kernel, target, true );
		final ImagePlus results = MonogenicUtils.toImagePlus( target, "Results" );
		results.show();

		/*
		 * Live update
		 */

		final MouseAdapter ma = new MouseAdapter()
		{
			private double s = sigma;

			@Override
			public void mousePressed( final MouseEvent e )
			{
				refresh( e );
			};

			@Override
			public void mouseDragged( final MouseEvent e )
			{
				refresh( e );
			};

			@Override
			public void mouseWheelMoved( final MouseWheelEvent e )
			{
				s = Math.max( 1, s + e.getWheelRotation() );
				refresh( e );
			};

			private void refresh( final MouseEvent e )
			{
				final int x = kernelImp.getCanvas().offScreenX( e.getX() );
				final int y = kernelImp.getCanvas().offScreenY( e.getY() );

				kip.set( 0 );
				kip.set( x, y, pixelVal );
				kip.blurGaussian( s );
				kernelImp.resetDisplayRange();
				kernelImp.updateAndDraw();

				convolver.convolve( kernel, target, true );
				MonogenicUtils.writeToImagePlus( target, results );
				results.resetDisplayRange();
				results.updateAndDraw();
			}
		};

		kernelImp.getCanvas().addMouseListener( ma );
		kernelImp.getCanvas().addMouseWheelListener( ma );
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
