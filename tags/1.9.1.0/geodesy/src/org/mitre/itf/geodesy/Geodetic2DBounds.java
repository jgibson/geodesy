/****************************************************************************************
 *  Geodetic2DBounds.java
 *
 *  Created: Feb 19, 2007
 *
 *  @author Paul Silvey
 *
 *  (C) Copyright MITRE Corporation 2007
 *
 *  The program is provided "as is" without any warranty express or implied, including 
 *  the warranty of non-infringement and the implied warranties of merchantibility and 
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any 
 *  damages suffered by you as a result of using the Program.  In no event will the 
 *  Copyright owner be liable for any special, indirect or consequential damages or 
 *  lost profits even if the Copyright owner has been advised of the possibility of 
 *  their occurrence.
 *
 ***************************************************************************************/
package org.mitre.itf.geodesy;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Geodetic2DBounds class is used to store min and max values for a pair of Geodetic2DPoint
 * coordinates. Since the range of longitudes may cross the international date line,
 * coordinates are identified by their compass directions.  The signed value of the
 * west-most longitude may therefore be greater than the signed value of the east-most
 * longitude. A bounding box may be degenerate if it is only defined by a single point,
 * and it may be grown around a set of points by successive calls to the include method.
 */
public class Geodetic2DBounds implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory.getLogger(Geodetic2DBounds.class);

    public Longitude westLon;
    public Latitude southLat;
    public Longitude eastLon;
    public Latitude northLat;

    /**
     * The default constructor returns a bounding box containing only the 0-0 point.
     */
    public Geodetic2DBounds() {
        westLon = new Longitude();
        southLat = new Latitude();
        eastLon = new Longitude();
        northLat = new Latitude();
    }

    /**
     * This constructor will accept west and east Geodetic2DPoint coordinates and set the
     * bounding box limits accordingly.  Note that the source coordinate components are
     * used by reference (i.e. they are not cloned).  The first coordinate is used to set
     * the west Longitude even if its value is greater than the east, so boxes that span
     * the international date line can be accomodated by this convention of listing lons
     * in reverse order.  On the other hand, latitude values are normalized so that south
     * is always less than north.
     *
     * @param westCoordinate western most point
     * @param eastCoordinate eastern most point
     * @throws NullPointerException if westCoordinate or eastCoordinate is null
     */
    public Geodetic2DBounds(Geodetic2DPoint westCoordinate, Geodetic2DPoint eastCoordinate) {
        // Accept Longitude in the west-east order given, to allow international date line span
        westLon = westCoordinate.getLongitude();
        eastLon = eastCoordinate.getLongitude();
        // Normalize Latitude, in case polar projection results in min-max getting flipped
        if (westCoordinate.getLatitude().inRadians < eastCoordinate.getLatitude().inRadians) {
            southLat = westCoordinate.getLatitude();
            northLat = eastCoordinate.getLatitude();
        } else {
            southLat = eastCoordinate.getLatitude();
            northLat = westCoordinate.getLatitude();
        }
    }

    /**
     * This constructor takes a single Geodetic2DPoint seed point and constructs a degenerate
     * bounding box that contains it.  It is primarily used with the include method to grow a
     * bounding box around a set of points.
     *
     * @param seedPoint Initial point to use as limits of bounding box.
     * @throws NullPointerException if seedPoint is null
     */
    public Geodetic2DBounds(Geodetic2DPoint seedPoint) {
        westLon = seedPoint.getLongitude();
        eastLon = westLon;
        southLat = seedPoint.getLatitude();
        northLat = southLat;
    }

    /**
     * This constructor takes a single Geodetic2DBounds as a seed and constructs a new
     * bounding box by cloning its values. It is primarily used with the include methods
     * to grow a bounding box around a set of points or other bounding boxes.
     *
     * @param seedBBox Initial bounding box to use as limits of bounding box.
     * @throws NullPointerException if seedPoint is null
     */
    public Geodetic2DBounds(Geodetic2DBounds seedBBox) {
        westLon = seedBBox.westLon;
        eastLon = seedBBox.eastLon;
        southLat = seedBBox.southLat;
        northLat = seedBBox.northLat;
    }

    /**
     * This constructor takes a geodetic center point and radius in meters, and
     * constructs a new Geodetic2DBounds bounding box that inscribes (contains) the
     * specified circle.
     *
     * @param center         Geodetic2DPoint at the center of the inscribed circle
     * @param radiusInMeters radius (in meters) of the inscribed circle
     * @throws NullPointerException     if center is null
     * @throws IllegalArgumentException error if circle position or size is unacceptable
     */
    public Geodetic2DBounds(Geodetic2DPoint center, double radiusInMeters)
            throws IllegalArgumentException {
        Geodetic2DBounds bbox = new Geodetic2DBounds(center);
        Angle compassDirection = new Angle(-Math.PI); // Start from South direction
        Angle inc = new Angle(Math.PI / 2.0);         // 90 degree increments
        Geodetic2DArc arc = new Geodetic2DArc(center, radiusInMeters, compassDirection);
        for (int i = 0; i < 4; i++) {
            bbox.include(arc.getPoint2());
            compassDirection = compassDirection.add(inc);
            arc.setForwardAzimuth(compassDirection);
        }
        westLon = bbox.westLon;
        eastLon = bbox.eastLon;
        southLat = bbox.southLat;
        northLat = bbox.northLat;
    }

    /**
     * This method is used to extend this bounding box to include a new point. If the new
     * point is already inside the existing bounding box, no change will result. The bounding
     * box is always extended by the smallest amount necessary to include the new point.
     * Sometimes this will cause the bounding box to wrap around the international date line.
     *
     * @param newPoint new Geodetic2DPoint point to include in this bounding box.
     */
    public void include(Geodetic2DPoint newPoint) {
        // Longitude requires proximity test to edges to see if date line wrap is needed
        Longitude lon = newPoint.getLongitude();
        if (!lon.inInterval(westLon, eastLon)) {
            // new lon is outside of the existing interval, so compare angular distances
            double headDist = eastLon.radiansEast(lon);
            double tailDist = lon.radiansEast(westLon);
            // if equidistant, prefer smaller lon value to be west end of new interval
            if (headDist < tailDist) eastLon = lon;
            else if (tailDist < headDist) westLon = lon;
            else if (westLon.inRadians < lon.inRadians) eastLon = lon;
            else westLon = lon;
        }
        // Latitude is a simple comparison and extension if appropriate
        Latitude lat = newPoint.getLatitude();
        double latRad = lat.inRadians;
        if (northLat.inRadians < latRad) northLat = lat;
        else if (southLat.inRadians > latRad) southLat = lat;
    }

    private static final String OOPS = "Impossible bounding box topology case";

    /**
     * This method is used to extend this bounding box to include another bounding box. If the
     * newly specified bounding box is inside the existing bounding box, no change will result.
     * This bounding box is always extended by the smallest amount necessary to include the new
     * bounding box. Sometimes this will cause this bounding box to wrap around the international
     * date line.
     *
     * @param bbox additional Geodetic2DBounds bounding box to include in this bounding box.
     */
    public void include(Geodetic2DBounds bbox) {
        // Extend Latitude interval endpoints if current values are exceeded
        if (bbox.northLat.inRadians > this.northLat.inRadians)
            this.northLat = bbox.northLat;
        if (bbox.southLat.inRadians < this.southLat.inRadians)
            this.southLat = bbox.southLat;

        // If current Longitude interval (A) is already the whole globe, just leave
        Longitude westA = this.westLon;
        Longitude eastA = this.eastLon;
        if ((westA.inRadians == -Math.PI) && (eastA.inRadians == -Math.PI)) return;
        // If new Longitude interval (B) is already the whole globe, use it and leave
        Longitude westB = bbox.westLon;
        Longitude eastB = bbox.eastLon;
        if ((westB.inRadians == -Math.PI) && (eastB.inRadians == -Math.PI)) {
            this.westLon = westB;
            this.eastLon = eastB;
            return;
        }

        // Cases are mostly distinguished by point-in-interval tests
        boolean AEndsInB = eastA.inInterval(westB, eastB);
        boolean AStartsInB = westA.inInterval(westB, eastB);
        boolean BEndsInA = eastB.inInterval(westA, eastA);
        boolean BStartsInA = westB.inInterval(westA, eastA);

        // Topological Longitude cases, some require angular distance calcs as well
        // Note this logic was determined using a truth table and circle diagrams
        if (BStartsInA && BEndsInA) {
            if (AStartsInB && AEndsInB) {
                // If not equal, intervals complement each other to complete the circle
                if (!westA.equals(westB)) {
                    this.westLon = new Longitude(-Math.PI);
                    this.eastLon = new Longitude(+Math.PI);
                }
            } else if (!AStartsInB && !AEndsInB) {
                // Either A covers B (OK as is) or B completes the circle for A
                if (westB.radiansEast(eastB) > westA.radiansEast(eastA)) {
                    this.westLon = new Longitude(-Math.PI);
                    this.eastLon = new Longitude(+Math.PI);
                }
            }
        } else if (!BStartsInA && !BEndsInA) {
            //assert(AStartsInB == AEndsInB);     // other cases should be impossible
            if (AStartsInB && AEndsInB) {
                // Either B covers A or A completes the circle for B
                if (westA.radiansEast(eastA) > westB.radiansEast(eastB)) {
                    this.westLon = new Longitude(-Math.PI);
                    this.eastLon = new Longitude(+Math.PI);
                } else {
                    this.westLon = westB;
                    this.eastLon = eastB;
                }
            } else if (!AStartsInB && !AEndsInB) {
                // A and B are disjoint intervals, so use inter-interval distance calcs
                double B2A = eastB.radiansEast(westA);
                double A2B = eastA.radiansEast(westB);
                if (B2A < A2B) this.westLon = westB;
                else this.eastLon = eastB;
            } else log.error(OOPS);
        } else if (BEndsInA && AStartsInB) {
            // B precedes and overlaps A
            this.westLon = westB;
        } else if (AEndsInB && BStartsInA) {
            // A precedes and overlaps B
            this.eastLon = eastB;
        } else log.error(OOPS);
    }

    /**
     * This predicate method determines whether the specified geodetic point is
     * contained within this bounding box.
     *
     * @param testPoint Geodetic2DPoint to test for containment within this bounding box
     * @return true if specified point is within this Geodetic2DBounds, false otherwise.
     */
    public boolean contains(Geodetic2DPoint testPoint) {
        return (testPoint.getLatitude().inInterval(this.southLat, this.northLat) &&
                testPoint.getLongitude().inInterval(this.westLon, this.eastLon));
    }

    /**
     * This predicate method determines whether the specified geodetic bounding box is
     * contained within this bounding box.
     *
     * @param testBox Geodetic2DBounds to test for containment within this bounding box
     * @return true if the specified bounding box is contained within this bounding box
     */
    public boolean contains(Geodetic2DBounds testBox) {
        return (testBox.southLat.inInterval(this.southLat, this.northLat) &&
                testBox.northLat.inInterval(this.southLat, this.northLat) &&
                testBox.westLon.inInterval(this.westLon, this.eastLon) &&
                testBox.eastLon.inInterval(this.westLon, this.eastLon));
    }

    /**
     * This predicate method determines whether the specified geodetic bounding box has
     * any area in common with this bounding box, that is, whether they intersect or not.
     *
     * @param testBox Geodetic2DBounds to test for intersection with this bounding box
     * @return true if the specified bounding box intersects this bounding box
     */
    public boolean intersects(Geodetic2DBounds testBox) {
        return !((testBox.southLat.inRadians >= this.northLat.inRadians) ||
                (testBox.northLat.inRadians <= this.southLat.inRadians) ||
                ((testBox.westLon.inInterval(this.eastLon, this.westLon)) &&
                        (testBox.eastLon.inInterval(this.eastLon, this.westLon)) &&
                        (!this.westLon.inInterval(testBox.westLon, testBox.eastLon))));
    }

    /**
     * This method is used to determine the geodetic point that lies at the center of
     * this bounding box.
     *
     * @return Geodetic2DPoint that lies at the center of this Geodetic2DBounds box
     */
    public Geodetic2DPoint getCenter() {
        double westLonRad = this.westLon.inRadians;
        double eastLonRad = this.eastLon.inRadians;
        // If longitudes wrap, adjust east to be greater than west before calc
        if (westLonRad > eastLonRad) eastLonRad += Angle.TWO_PI;
        double centLonRad = westLonRad + ((eastLonRad - westLonRad) / 2.0);
        double southLatRad = this.southLat.inRadians;
        double northLatRad = this.northLat.inRadians;
        double centLatRad = southLatRad + ((northLatRad - southLatRad) / 2.0);
        // Note that Longitude constructor will re-normalize angle if > 360�
        return new Geodetic2DPoint(new Longitude(centLonRad), new Latitude(centLatRad));
    }

    /**
     * Getter for property westLon.
     *
     * @return Value of property westLon.
     */
    public Longitude getWestLon() {
        return this.westLon;
    }

    /**
     * Setter for property westLon.
     *
     * @param westLon New value of property westLon.
     */
    public void setWestLon(final Longitude westLon) {
        this.westLon = westLon;
    }

    /**
     * Getter for property eastLon.
     *
     * @return Value of property eastLon.
     */
    public Longitude getEastLon() {
        return this.eastLon;
    }

    /**
     * Setter for property eastLon.
     *
     * @param eastLon New value of property eastLon.
     */
    public void setEastLon(final Longitude eastLon) {
        this.eastLon = eastLon;
    }

    /**
     * Getter for property southLat.
     *
     * @return Value of property southLat.
     */
    public Latitude getSouthLat() {
        return this.southLat;
    }

    /**
     * Setter for property southLat.
     *
     * @param southLat New value of property southLat.
     */
    public void setSouthLat(final Latitude southLat) {
        this.southLat = southLat;
    }

    /**
     * Getter for property northLat.
     *
     * @return Value of property northLat.
     */
    public Latitude getNorthLat() {
        return this.northLat;
    }

    /**
     * Setter for property northLat.
     *
     * @param northLat New value of property northLat.
     */
    public void setNorthLat(final Latitude northLat) {
        this.northLat = northLat;
    }

    /**
     * Returns a hash code for this <code>Geodetic2DBounds</code> object. The
     * result is the exclusive OR of the latitude and longitude values to maintain
     * the general contract for the hashCode method, which states
     * that equal objects must have equal hash codes.
     *
     * @return a <code>hash code</code> value for this object.
     */
    public int hashCode() {
        return westLon.hashCode() ^ eastLon.hashCode() ^ southLat.hashCode() ^ northLat.hashCode();
    }

    /**
     * Compares this object against the specified object.  The result
     * is <code>true</code> if and only if the argument is not
     * <code>null</code> and is a <code>Geodetic2DBounds</code> object that
     * has the same latitude and longitude values.
     *
     * @param that the object to compare with.
     * @return <code>true</code> if the objects are the same;
     *         <code>false</code> otherwise.
     */
    public boolean equals(Object that) {
        return (that instanceof Geodetic2DBounds) && equals((Geodetic2DBounds) that);
    }

    /**
     * The equals method tests for bounding box coordinate numeric equality
     *
     * @param that Geodetic2DBound object to compare to this one
     * @return true if specified Geodetic2DBounds is spatially equivalent to this one
     */
    public boolean equals(Geodetic2DBounds that) {
        return (this.westLon.equals(that.westLon) && this.eastLon.equals(that.eastLon) &&
                this.southLat.equals(that.southLat) && this.northLat.equals(that.northLat));
    }

    /**
     * The equals method tests for bounding box coordinate numeric equality.
     * An error threshold is also passed in to help distinguish between two values
     * that are close numerically.
     *
     * @param that    Geodetic2DBound object to compare to this one
     * @param epsilon the epsilon value for error threshold
     * @return true if specified Geodetic2DBounds is spatially equivalent to this one
     */
    public boolean equals(Geodetic2DBounds that, double epsilon) {
        return (Angle.equals(this.westLon.inDegrees(), that.westLon.inDegrees(), epsilon) &&
                Angle.equals(this.eastLon.inDegrees(), that.eastLon.inDegrees(), epsilon) &&
                Angle.equals(this.southLat.inDegrees(), that.southLat.inDegrees(), epsilon) &&
                Angle.equals(this.northLat.inDegrees(), that.northLat.inDegrees(), epsilon));
    }

    /**
     * The toString method formats the bounding box coordinates for printing, as SW .. NE points.
     *
     * @return String representation of corner points of bounding box.
     */
	@Override
    public String toString() {
        return "(" + westLon + ", " + southLat + ") .. (" + eastLon + ", " + northLat + ")";
    }
}