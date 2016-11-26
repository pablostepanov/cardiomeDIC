package fi.candysoft.fingers;

import java.awt.AWTException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import org.opencv.videoio.VideoCapture;

public class FingersVideo extends JPanel {

	private JFrame frame = new JFrame("Fingers");
	private JLabel label = new JLabel();

	private static String STRING = "Waiting for action";
	private static final long serialVersionUID = 1L;
	private static Point last = new Point();
	private static boolean close = false;
	private static boolean act = false;
	private static long current = 0;
	private static long prev = 0;
	private static boolean start = false;

	public FingersVideo() {

	}

	public void setframe(final VideoCapture webcam) {
		frame.setSize(1024, 768);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		frame.getContentPane().add(label);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.out.println("Closed");
				close = true;
				webcam.release();
				e.getWindow().dispose();
			}
		});
	}

	public void streamToFrame(Mat matframe) {
		MatOfByte matOfByte = new MatOfByte();
		Imgcodecs.imencode(".JPG", matframe, matOfByte);
		InputStream inputStream = new ByteArrayInputStream(matOfByte.toArray());
		try {
			BufferedImage bufferedImage = ImageIO.read(inputStream);
			label.setIcon(new ImageIcon(bufferedImage));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param kd
	 *            Size of the structuring element.
	 * @param erosionResult
	 *            preliminary filtered image with Erosion filter
	 * @return an n-dimensional dense numerical single-channel or multi-channel
	 *         array after Dilation filtering
	 */
	private Mat filterDilation(int kd, Mat erosionResult) {
		Mat dilationResult = new Mat();
		Imgproc.dilate(erosionResult, dilationResult,
				Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(kd, kd)));
		return dilationResult;
	}

	/**
	 * 
	 * @param ke
	 *            Size of the structuring element.
	 * @param image
	 *            an n-dimensional dense numerical single-channel or
	 *            multi-channel array. It can be used to store real or
	 *            complex-valued vectors and matrices, grayscale or color
	 *            images,
	 * @return an n-dimensional dense numerical single-channel or multi-channel
	 *         array after filtering
	 */
	private Mat filterErosion(int ke, Mat image) {
		Mat result = new Mat();
		Imgproc.erode(image, result,
				Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(ke, ke)));
		return result;
	}

	public double calculateDistance(Point P1, Point P2) {
		double distance = Math
				.sqrt(((P1.x - P2.x) * (P1.x - P2.x)) + ((P1.y - P2.y) * (P1.y - P2.y)));

		return distance;
	}

	public double calcolaangolo(Point P1, Point P2, Point P3) {
		double angolo = 0;
		Point v1 = new Point();
		Point v2 = new Point();
		v1.x = P3.x - P1.x;
		v1.y = P3.y - P1.y;
		v2.x = P3.x - P2.x;
		v2.y = P3.y - P2.y;
		double dotproduct = (v1.x * v2.x) + (v1.y * v2.y);
		double length1 = Math.sqrt((v1.x * v1.x) + (v1.y * v1.y));
		double length2 = Math.sqrt((v2.x * v2.x) + (v2.y * v2.y));
		double angle = Math.acos(dotproduct / (length1 * length2));
		angolo = angle * 180 / Math.PI;

		return angolo;
	}

	public Mat filtrocolorehsv(int h, int s, int v, int h1, int s1, int v1, Mat image) {
		Mat result = new Mat();
		if (image != null) {
			Core.inRange(image, new Scalar(h, s, v), new Scalar(h1, s1, v1), result);
		} else {
			System.out.println("Errore immagine");
		}
		return result;
	}

	public Mat skindetction(Mat orig) {
		Mat maschera = new Mat();
		Mat risultato = new Mat();
		Core.inRange(orig, new Scalar(0, 0, 0), new Scalar(30, 30, 30), risultato);
		Imgproc.cvtColor(orig, maschera, Imgproc.COLOR_BGR2HSV);
		for (int i = 0; i < maschera.size().height; i++) {
			for (int j = 0; j < maschera.size().width; j++) {
				if (maschera.get(i, j)[0] < 19 || maschera.get(i, j)[0] > 150
						&& maschera.get(i, j)[1] > 25 && maschera.get(i, j)[1] < 220) {

					risultato.put(i, j, 255, 255, 255);

				} else {
					risultato.put(i, j, 0, 0, 0);
				}
			}

		}

		return risultato;

	}
	
	
	/**
	 * 
	 * @param originalImage
	 * @param filteredImage
	 * @param disegna
	 * @param disegnatutto
	 * @param filtropixel
	 * @return
	 */

	public List<MatOfPoint> drawContours(final Mat originalImage, final Mat filteredImage,
			final boolean disegna, final boolean disegnatutto, final int filtropixel) {
		List<MatOfPoint> contours = new LinkedList<MatOfPoint>();
		List<MatOfPoint> contoursbig = new LinkedList<MatOfPoint>();
		Mat hierarchy = new Mat();

		Imgproc.findContours(filteredImage, contours, hierarchy, Imgproc.RETR_EXTERNAL,
				Imgproc.CHAIN_APPROX_NONE, new Point(0, 0));

		for (int i = 0; i < contours.size(); i++) {
			if (contours.get(i).size().height > filtropixel) {
				contoursbig.add(contours.get(i));
				if (disegna && !disegnatutto)
					Imgproc.drawContours(originalImage, contours, i, new Scalar(0, 255, 0), 2,
							8, hierarchy, 0, new Point());
			}

			if (disegnatutto && !disegna)
				Imgproc.drawContours(originalImage, contours, i, new Scalar(0, 255, 255), 2, 8,
						hierarchy, 0, new Point());

		}
		return contoursbig;
	}

	public List<Point> listacontorno(Mat immagine, int filtropixel) {
		List<MatOfPoint> contours = new LinkedList<MatOfPoint>();
		List<MatOfPoint> contoursbig = new LinkedList<MatOfPoint>();
		List<Point> listapunti = new LinkedList<Point>();
		Mat hierarchy = new Mat();

		Imgproc.findContours(immagine, contours, hierarchy, Imgproc.RETR_EXTERNAL,
				Imgproc.CHAIN_APPROX_NONE, new Point(0, 0));

		for (int i = 0; i < contours.size(); i++) {
			// System.out.println("Dimensione
			// contorni"+contours.get(i).size().height);
			if (contours.get(i).size().height > filtropixel) {
				contoursbig.add(contours.get(i));
			}

		}
		if (contoursbig.size() > 0) {

			listapunti = contoursbig.get(0).toList();

		}
		return listapunti;
	}

	public List<Point> inviluppodifetti(Mat image, List<MatOfPoint> contours, boolean disegna,
			int sogliaprofondita) {
		List<Point> defects = new LinkedList<Point>();

		for (int i = 0; i < contours.size(); i++) {
			MatOfInt hull_ = new MatOfInt();
			MatOfInt4 convexityDefects = new MatOfInt4();

			@SuppressWarnings("unused")
			List<Point> punticontorno = new LinkedList<Point>();
			punticontorno = contours.get(i).toList();

			Imgproc.convexHull(contours.get(i), hull_);

			if (hull_.size().height >= 4) {

				Imgproc.convexityDefects(contours.get(i), hull_, convexityDefects);

				List<Point> pts = new ArrayList<Point>();
				MatOfPoint2f pr = new MatOfPoint2f();
				Converters.Mat_to_vector_Point(contours.get(i), pts);
				// rettangolo
				pr.create((pts.size()), 1, CvType.CV_32S);
				pr.fromList(pts);
				if (pr.height() > 10) {
					RotatedRect r = Imgproc.minAreaRect(pr);
					Point[] rect = new Point[4];
					r.points(rect);

					Imgproc.line(image, rect[0], rect[1], new Scalar(0, 100, 0), 2);
					Imgproc.line(image, rect[0], rect[3], new Scalar(0, 100, 0), 2);
					Imgproc.line(image, rect[1], rect[2], new Scalar(0, 100, 0), 2);
					Imgproc.line(image, rect[2], rect[3], new Scalar(0, 100, 0), 2);
					Imgproc.rectangle(image, r.boundingRect().tl(), r.boundingRect().br(),
							new Scalar(50, 50, 50));
				}
				// fine rettangolo

				int[] buff = new int[4];
				int[] zx = new int[1];
				int[] zxx = new int[1];
				for (int i1 = 0; i1 < hull_.size().height; i1++) {
					if (i1 < hull_.size().height - 1) {
						hull_.get(i1, 0, zx);
						hull_.get(i1 + 1, 0, zxx);
					} else {
						hull_.get(i1, 0, zx);
						hull_.get(0, 0, zxx);
					}
					if (disegna) {
						Imgproc.line(image, pts.get(zx[0]), pts.get(zxx[0]),
								new Scalar(140, 140, 140), 2);

					}
				}

				for (int i1 = 0; i1 < convexityDefects.size().height; i1++) {
					convexityDefects.get(i1, 0, buff);
					if (buff[3] / 256 > sogliaprofondita) {
						if (pts.get(buff[2]).x > 0 && pts.get(buff[2]).x < 1024
								&& pts.get(buff[2]).y > 0 && pts.get(buff[2]).y < 768) {
							defects.add(pts.get(buff[2]));
							Imgproc.circle(image, pts.get(buff[2]), 6, new Scalar(0, 255, 0));
							if (disegna) {
								Imgproc.circle(image, pts.get(buff[2]), 6,
										new Scalar(0, 255, 0));
							}

						}
					}
				}
				if (defects.size() < 3) {
					int dim = pts.size();
					Imgproc.circle(image, pts.get(0), 3, new Scalar(0, 255, 0), 2);
					Imgproc.circle(image, pts.get(0 + dim / 4), 3, new Scalar(0, 255, 0), 2);
					defects.add(pts.get(0));
					defects.add(pts.get(0 + dim / 4));

				}
			}
		}
		return defects;
	}

	/**
	 * Finds a center of a 2D point set.
	 * 
	 * @param image
	 * @param points
	 * @return center center of the points
	 */
	public Point findCenter(Mat image, List<Point> points) {
		MatOfPoint2f pr = new MatOfPoint2f();
		Point center = new Point();
		float[] radius = new float[1];
		pr.create((points.size()), 1, CvType.CV_32S);
		pr.fromList(points);

		if (pr.size().height > 0) {
			start = true;
			Imgproc.minEnclosingCircle(pr, center, radius);

		} else {
			start = false;
		}
		return center;

	}

	public List<Point> dita(Mat immagine, List<Point> punticontorno, Point center) {
		List<Point> puntidita = new LinkedList<Point>();
		List<Point> dita = new LinkedList<Point>();
		int intervallo = 55;
		for (int j = 0; j < punticontorno.size(); j++) {
			Point prec = new Point();
			Point vertice = new Point();
			Point next = new Point();
			vertice = punticontorno.get(j);
			if (j - intervallo > 0) {

				prec = punticontorno.get(j - intervallo);
			} else {
				int a = intervallo - j;
				prec = punticontorno.get(punticontorno.size() - a - 1);
			}
			if (j + intervallo < punticontorno.size()) {
				next = punticontorno.get(j + intervallo);
			} else {
				int a = j + intervallo - punticontorno.size();
				next = punticontorno.get(a);
			}

			Point v1 = new Point();
			Point v2 = new Point();
			v1.x = vertice.x - next.x;
			v1.y = vertice.y - next.y;
			v2.x = vertice.x - prec.x;
			v2.y = vertice.y - prec.y;
			double dotproduct = (v1.x * v2.x) + (v1.y * v2.y);
			double length1 = Math.sqrt((v1.x * v1.x) + (v1.y * v1.y));
			double length2 = Math.sqrt((v2.x * v2.x) + (v2.y * v2.y));
			double angle = Math.acos(dotproduct / (length1 * length2));
			angle = angle * 180 / Math.PI;
			if (angle < 60) {
				double centroprec = Math.sqrt(((prec.x - center.x) * (prec.x - center.x))
						+ ((prec.y - center.y) * (prec.y - center.y)));
				double centrovert = Math.sqrt(((vertice.x - center.x) * (vertice.x - center.x))
						+ ((vertice.y - center.y) * (vertice.y - center.y)));
				double centronext = Math.sqrt(((next.x - center.x) * (next.x - center.x))
						+ ((next.y - center.y) * (next.y - center.y)));
				if (centroprec < centrovert && centronext < centrovert) {

					puntidita.add(vertice);
					// Core.circle(immagine, vertice, 2, new Scalar(200,0,230));

					// Core.line(immagine, vertice, center, new
					// Scalar(0,255,255));
				}
			}
		}

		Point media = new Point();
		media.x = 0;
		media.y = 0;
		int med = 0;
		boolean t = false;
		if (puntidita.size() > 0) {
			double dif = Math.sqrt(((puntidita.get(0).x - puntidita.get(puntidita.size() - 1).x)
					* (puntidita.get(0).x - puntidita.get(puntidita.size() - 1).x))
					+ ((puntidita.get(0).y - puntidita.get(puntidita.size() - 1).y)
							* (puntidita.get(0).y - puntidita.get(puntidita.size() - 1).y)));
			if (dif <= 20) {
				t = true;
			}
		}
		for (int i = 0; i < puntidita.size() - 1; i++) {

			double d = Math.sqrt(((puntidita.get(i).x - puntidita.get(i + 1).x)
					* (puntidita.get(i).x - puntidita.get(i + 1).x))
					+ ((puntidita.get(i).y - puntidita.get(i + 1).y)
							* (puntidita.get(i).y - puntidita.get(i + 1).y)));

			if (d > 20 || i + 1 == puntidita.size() - 1) {
				Point p = new Point();

				p.x = (int) (media.x / med);
				p.y = (int) (media.y / med);

				// if(p.x>0 && p.x<1024 && p.y<768 && p.y>0){

				dita.add(p);
				// }

				if (t && i + 1 == puntidita.size() - 1) {
					Point ult = new Point();
					if (dita.size() > 1) {
						ult.x = (dita.get(0).x + dita.get(dita.size() - 1).x) / 2;
						ult.y = (dita.get(0).y + dita.get(dita.size() - 1).y) / 2;
						dita.set(0, ult);
						dita.remove(dita.size() - 1);
					}
				}
				med = 0;
				media.x = 0;
				media.y = 0;
			} else {

				media.x = (media.x + puntidita.get(i).x);
				media.y = (media.y + puntidita.get(i).y);
				med++;

			}
		}

		return dita;
	}

	public void disegnaditacentropalmo(Mat immagine, Point center, Point dito,
			List<Point> dita) {

		Imgproc.line(immagine, new Point(150, 50), new Point(730, 50), new Scalar(255, 0, 0),
				2);
		Imgproc.line(immagine, new Point(150, 380), new Point(730, 380), new Scalar(255, 0, 0),
				2);
		Imgproc.line(immagine, new Point(150, 50), new Point(150, 380), new Scalar(255, 0, 0),
				2);
		Imgproc.line(immagine, new Point(730, 50), new Point(730, 380), new Scalar(255, 0, 0),
				2);
		if (dita.size() == 1) {
			Imgproc.line(immagine, center, dito, new Scalar(0, 255, 255), 4);
			Imgproc.circle(immagine, dito, 3, new Scalar(255, 0, 255), 3);
			Imgproc.putText(immagine, dito.toString(), dito, Core.FONT_HERSHEY_COMPLEX, 1,
					new Scalar(0, 200, 255));

		} else {
			for (int i = 0; i < dita.size(); i++) {
				Imgproc.line(immagine, center, dita.get(i), new Scalar(0, 255, 255), 4);
				Imgproc.circle(immagine, dita.get(i), 3, new Scalar(255, 0, 255), 3);
			}
		}
		Imgproc.circle(immagine, center, 3, new Scalar(0, 0, 255), 3);
		Imgproc.putText(immagine, center.toString(), center, Core.FONT_HERSHEY_COMPLEX, 1,
				new Scalar(0, 200, 255));

	}

	public Point filtromediamobile(List<Point> buffer, Point attuale) {
		Point media = new Point();
		media.x = 0;
		media.y = 0;
		for (int i = buffer.size() - 1; i > 0; i--) {
			buffer.set(i, buffer.get(i - 1));
			media.x = media.x + buffer.get(i).x;
			media.y = media.y + buffer.get(i).y;
		}
		buffer.set(0, attuale);
		media.x = (media.x + buffer.get(0).x) / buffer.size();
		media.y = (media.y + buffer.get(0).y) / buffer.size();
		return media;
	}

	public static void main(String[] args) throws InterruptedException, AWTException {

		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		FingersVideo fingersVideo = new FingersVideo();
		VideoCapture webcam = new VideoCapture(0);
		fingersVideo.setframe(webcam);
		Mat image = new Mat();
		Mat hsvFilteredImage = new Mat();
		Mat erosionFilteredImage = new Mat();
		Mat fyllyFilteredImage = new Mat();

		Point center = new Point();
		Point dito = new Point();
		List<Point> buffer = new LinkedList<Point>();
		List<Point> bufferdita = new LinkedList<Point>();
		List<Point> dita = new LinkedList<Point>();

		while (true && !close) {

			if (!webcam.isOpened() && !close) {
				System.out.println("Camera Error");
			} else {
				System.out.println(
						"webcam is up and running. System time = " + new Date().getTime());

				List<Point> points = new LinkedList<Point>();
				System.out.println("difetti = " + points.size());
				if (!close) {
					webcam.read(image);

					hsvFilteredImage = fingersVideo.filtrocolorehsv(0, 0, 0, 180, 255, 40,
							image);

					erosionFilteredImage = fingersVideo.filterErosion(7, hsvFilteredImage);

					fyllyFilteredImage = fingersVideo.filterDilation(2, erosionFilteredImage);

					List<MatOfPoint> drawnContours = fingersVideo.drawContours(image,
							fyllyFilteredImage, false, false, 450);

					points = fingersVideo.inviluppodifetti(image, drawnContours, false, 5);

					if (buffer.size() < 7) {
						buffer.add(fingersVideo.findCenter(image, points));
					} else {
						center = fingersVideo.filtromediamobile(buffer,
								fingersVideo.findCenter(image, points));

					}

					dita = fingersVideo.dita(image,
							fingersVideo.listacontorno(fyllyFilteredImage, 200), center);

					if (dita.size() == 1 && bufferdita.size() < 5) {
						bufferdita.add(dita.get(0));
						dito = dita.get(0);
					} else {
						if (dita.size() == 1) {
							dito = fingersVideo.filtromediamobile(bufferdita, dita.get(0));
						}
					}

					fingersVideo.disegnaditacentropalmo(image, center, dito, dita);

					fingersVideo.streamToFrame(image);

				}
			}

		}

	}
}
