package net.imglib2.monogenic;

import java.util.concurrent.atomic.AtomicInteger;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.fft.FFTFunctions;
import net.imglib2.iterator.LocalizingZeroMinIntervalIterator;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.Type;
import net.imglib2.util.Util;

/**
 * Taken from {@link FFTFunctions}.
 */
@SuppressWarnings( "deprecation" )
public class QuadrantArranger
{
	final private static < T extends Type< T >> void rearrangeQuadrantDim( final RandomAccessibleInterval< T > fftImage, final int dim, final boolean forward, final int numThreads )
	{
		final int numDimensions = fftImage.numDimensions();

		if ( fftImage.dimension( dim ) % 2 == 1 )
		{
			rearrangeQuadrantDimOdd( fftImage, dim, forward, numThreads );
			return;
		}

		final AtomicInteger ai = new AtomicInteger( 0 );
		final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );

		for ( int ithread = 0; ithread < threads.length; ++ithread )
			threads[ ithread ] = new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final int myNumber = ai.getAndIncrement();

					final int sizeDim = ( int ) fftImage.dimension( dim );
					final int halfSizeDim = sizeDim / 2;

					// HACK: Explicit assignment is needed for OpenJDK javac.
					final T fftImageType = Util.getTypeFromInterval( fftImage );
					final T buffer = fftImageType.createVariable();

					final RandomAccess< T > cursor1 = fftImage.randomAccess();
					final RandomAccess< T > cursor2 = fftImage.randomAccess();

					/**
					 * Here we use a LocalizingZeroMinIntervalIterator to
					 * iterate through all dimensions except the one we are
					 * computing the fft in
					 */
					final int[] fakeSize = new int[ numDimensions - 1 ];
					final int[] tmp = new int[ numDimensions ];

					// get all dimensions except the one we are currently
					// swapping
					int countDim = 0;
					for ( int d = 0; d < numDimensions; ++d )
						if ( d != dim )
							fakeSize[ countDim++ ] = ( int ) fftImage.dimension( d );

					final LocalizingZeroMinIntervalIterator cursorDim = new LocalizingZeroMinIntervalIterator( fakeSize );

					// iterate over all dimensions except the one we are
					// computing the fft in, which is dim=0 here
					while ( cursorDim.hasNext() )
					{
						cursorDim.fwd();

						if ( cursorDim.getIntPosition( 0 ) % numThreads == myNumber )
						{
							// update all positions except for the one we are
							// currrently doing the fft on
							cursorDim.localize( fakeSize );

							tmp[ dim ] = 0;
							countDim = 0;
							for ( int d = 0; d < numDimensions; ++d )
								if ( d != dim )
									tmp[ d ] = fakeSize[ countDim++ ];

							// update the first cursor in the image to the zero
							// position
							cursor1.setPosition( tmp );

							// and a second one to the middle for rapid exchange
							// of the quadrants
							tmp[ dim ] = halfSizeDim;
							cursor2.setPosition( tmp );

							// now do a triangle-exchange
							for ( int i = 0; i < halfSizeDim - 1; ++i )
							{
								// cache first "half" to buffer
								buffer.set( cursor1.get() );

								// move second "half" to first "half"
								cursor1.get().set( cursor2.get() );

								// move data in buffer to second "half"
								cursor2.get().set( buffer );

								// move both cursors forward
								cursor1.fwd( dim );
								cursor2.fwd( dim );
							}
							// cache first "half" to buffer
							buffer.set( cursor1.get() );

							// move second "half" to first "half"
							cursor1.get().set( cursor2.get() );

							// move data in buffer to second "half"
							cursor2.get().set( buffer );
						}
					}
				}
			} );

		SimpleMultiThreading.startAndJoin( threads );
	}

	final private static < T extends Type< T >> void rearrangeQuadrantDimOdd( final RandomAccessibleInterval< T > fftImage, final int dim, final boolean forward, final int numThreads )
	{
		final int numDimensions = fftImage.numDimensions();

		final AtomicInteger ai = new AtomicInteger( 0 );
		final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );

		for ( int ithread = 0; ithread < threads.length; ++ithread )
			threads[ ithread ] = new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final int myNumber = ai.getAndIncrement();

					final int sizeDim = ( int ) fftImage.dimension( dim );
					final int sizeDimMinus1 = sizeDim - 1;
					final int halfSizeDim = sizeDim / 2;

					// HACK: Explicit assignment is needed for OpenJDK javac.
					final T fftImageType = Util.getTypeFromInterval( fftImage );
					final T buffer1 = fftImageType.createVariable();
					final T buffer2 = fftImageType.createVariable();

					final RandomAccess< T > cursor1 = fftImage.randomAccess();
					final RandomAccess< T > cursor2 = fftImage.randomAccess();

					/**
					 * Here we "misuse" a ArrayLocalizableCursor to iterate
					 * through all dimensions except the one we are computing
					 * the fft in
					 */
					final int[] fakeSize = new int[ numDimensions - 1 ];
					final int[] tmp = new int[ numDimensions ];

					// get all dimensions except the one we are currently
					// swapping
					int countDim = 0;
					for ( int d = 0; d < numDimensions; ++d )
						if ( d != dim )
							fakeSize[ countDim++ ] = ( int ) fftImage.dimension( d );

					final LocalizingZeroMinIntervalIterator cursorDim = new LocalizingZeroMinIntervalIterator( fakeSize );

					// iterate over all dimensions except the one we are
					// computing the fft in, which is dim=0 here
					while ( cursorDim.hasNext() )
					{
						cursorDim.fwd();

						if ( cursorDim.getIntPosition( 0 ) % numThreads == myNumber )
						{
							// update all positions except for the one we are
							// currrently doing the fft on
							cursorDim.localize( fakeSize );

							tmp[ dim ] = 0;
							countDim = 0;
							for ( int d = 0; d < numDimensions; ++d )
								if ( d != dim )
									tmp[ d ] = fakeSize[ countDim++ ];

							// update the first cursor in the image to the half
							// position
							tmp[ dim ] = halfSizeDim;
							cursor1.setPosition( tmp );

							// and a second one to the last pixel for rapid
							// exchange of the quadrants
							if ( forward )
								tmp[ dim ] = sizeDimMinus1;
							else
								tmp[ dim ] = 0;

							cursor2.setPosition( tmp );

							// cache middle entry
							buffer1.set( cursor1.get() );

							// now do a permutation
							for ( int i = 0; i < halfSizeDim; ++i )
							{
								// cache last entry
								buffer2.set( cursor2.get() );

								// overwrite last entry
								cursor2.get().set( buffer1 );

								// move cursor backward
								if ( forward )
									cursor1.bck( dim );
								else
									cursor1.fwd( dim );

								// cache middle entry
								buffer1.set( cursor1.get() );

								// overwrite middle entry
								cursor1.get().set( buffer2 );

								// move cursor backward
								if ( forward )
									cursor2.bck( dim );
								else
									cursor2.fwd( dim );
							}

							// set the last center pixel
							cursor2.setPosition( halfSizeDim, dim );
							cursor2.get().set( buffer1 );
						}
					}
				}
			} );

		SimpleMultiThreading.startAndJoin( threads );
	}

	final public static < T extends Type< T >> void rearrangeFFTQuadrants( final RandomAccessibleInterval< T > fftImage, final boolean forward, final int numThreads )
	{
//		rearrangeQuadrantFFTDimZero( fftImage, numThreads );

		for ( int d = 0; d < fftImage.numDimensions(); ++d )
			rearrangeQuadrantDim( fftImage, d, forward, numThreads );
	}
}
