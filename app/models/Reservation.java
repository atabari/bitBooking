package models;

import com.avaje.ebean.Model;
import com.fasterxml.jackson.annotation.JsonBackReference;
import helpers.ReservationStatus;
import play.Logger;
import play.data.format.Formats;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by gordan on 9/29/15.
 */
@Entity
public class Reservation extends Model {

    public static Finder<String, Reservation> finder = new Finder<String, Reservation>(Reservation.class);

    @Id
    public Integer id;

    public String payment_id;
    public String sale_id;

    public BigDecimal cost;

    @Formats.DateTime(pattern = "dd/MM/yyyy")
    @Column(columnDefinition = "datetime")
    public Date checkIn;

    @Formats.DateTime(pattern = "dd/MM/yyyy")
    @Column(columnDefinition = "datetime")
    public Date checkOut;

    public Integer status;

    public Boolean isRefunded;

    @Column(name = "notification", length = 1)
    public Integer notification;

    @Column(name = "updated_by", length = 50)
    public String updatedBy;
    @Column(name = "update_date", columnDefinition = "datetime")
    public Date updateDate;
    @Column(name = "created_by", length = 50, updatable = false)
    public String createdBy;
    @Column(name = "create_date", updatable = false, columnDefinition = "datetime")
    public Date createDate = new Date();

    @ManyToOne
    @JsonBackReference
    public Room room;

    @ManyToOne
    @JsonBackReference
    public AppUser user;


    public Reservation() {
    }

    public Reservation(Integer id, BigDecimal cost, Date checkIn, Date checkOut, Room room, String sale_id, AppUser user, Boolean isRefunded, Date timeOfReservation, String payment_id) {
        this.id = id;
        this.cost = cost;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = ReservationStatus.APPROVED;
        this.room = room;
        this.user = user;
        this.payment_id = payment_id;
        this.isRefunded = false;
        this.sale_id = sale_id;
    }

    public static Reservation findReservationById(Integer id) {
        Reservation reservation = finder.where().eq("id", id).findUnique();
        return reservation;
    }

    public static List<Reservation> findReservationByUserId(Integer id) {
        List<Reservation> reservationList = finder.where().eq("user_id", id).findList();
        return reservationList;
    }

    public static Room findRoomByReservation(Reservation reservation) {
        Room room = reservation.room;
        return room;
    }

    public static String findHotelNameByReservation(Reservation reservation) {
        return reservation.room.hotel.name;
    }

    public static Integer findNumberOfBedsByReservation(Reservation reservation) {
        return reservation.room.numberOfBeds;
    }

    public static List<Reservation> findReservationsByHotelAndUserIds(Integer hotelId, AppUser user) {
        List<Room> rooms = Room.findRoomsByHotelId(hotelId);
        List<Reservation> reservations = new ArrayList<>();

        for (int i = 0; i < rooms.size(); i++) {
            List<Reservation> allReservations = finder.where().eq("user_id", user.id).where().eq("room_id", rooms.get(i).id).findList();
            for (int j = 0; j < allReservations.size(); j++) {
                Reservation reservation = allReservations.get(j);
                if (reservation != null && reservation.status == ReservationStatus.COMPLETED) {
                    reservations.add(reservation);
                }
            }
//            Reservation reservation = finder.where().eq("user_id", user.id).where().eq("room_id", rooms.get(i).id).findUnique();
//            if (reservation != null && reservation.status == ReservationStatus.COMPLETED) {
//               reservations.add(reservation);
//            }
        }

        return reservations;
    }

    public static Integer getNumberOfPayedReservations(Integer sellerId) {
        Integer total = 0;
        List<Hotel> hotels = SiteStats.getManagersHotels(sellerId);
        for (Hotel hotel : hotels) {
            for (Room room : hotel.rooms) {
                for (Reservation res : room.reservations) {
                    if (res.status.equals(ReservationStatus.APPROVED)) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    /**
     * Finds all reservations that were paid by seller.
     * Creates new Date with current time.
     * Goes tru all reservations checks if checkOutDate is null, compares
     * currentDate and checkOutDate of every reservation. If reservationCheckoutDate
     * passed currentDate sets that reservation as completed.
     */
    public static void checkReservationExpiration() {
        List<Reservation> reservations = finder.where().eq("status", ReservationStatus.APPROVED).findList();

        final Date currentDate = new Date();

        for (Reservation reservation : reservations) {
            Date reservationCheckOutDate = reservation.checkOut;
            if (reservationCheckOutDate != null) {
                if (currentDate.after(reservationCheckOutDate)) {
                    reservation.status = ReservationStatus.COMPLETED;
                    reservation.updatedBy = "Auto complete reservation";
                    try {
                        reservation.update();
                    } catch (PersistenceException e) {
                        ErrorLogger.createNewErrorLogger("Could not update reservation status automatically. checkReservationExpiration()", e.getMessage());
                        Logger.info("Could not update reservation.");
                        Logger.info("Reservation made by " + reservation.user.firstname);
                        Logger.error("Error message " + e.getMessage());
                    }
                }
            }
        }
    }

    public static Reservation findByPaymentId(String id) {
        return finder.where().eq("payment_id", id).findUnique();
    }

    @Override
    public String toString() {
        return String.format("%s has reserved %s room from %s till %s for %s", user.firstname, room.name, checkIn, checkOut, cost);
    }

    public BigDecimal getCost() {
        cost = new BigDecimal(0);
        Date myDate = checkIn;
        for (Price price : room.prices) {
            while (myDate.compareTo(checkOut) < 0) {
                if (myDate.compareTo(price.dateFrom) >= 0 && myDate.compareTo(price.dateTo) <= 0) {
                    cost = cost.add(price.cost);
                } else {
                    break;
                }
                myDate = org.apache.commons.lang.time.DateUtils.addDays(myDate, 1);
            }
        }
        return cost;
    }

    public void setCreatedBy(String firstName, String lastName) {
        this.createdBy = firstName + " " + lastName;
    }

    public void setUpdatedBy(String firstName, String lastName) {
        this.updatedBy = firstName + " " + lastName;
    }

    @Override
    public void update() {
        updateDate = new Date();
        super.update();
    }

    /**
     * Finds last five approved reservations
     * @return
     */
    public static List<Hotel> getTopFiveRecentReservedHotels() {
        
        List<Reservation> lastReservations = finder.where().eq("status", ReservationStatus.APPROVED).findList();
        Collections.reverse(lastReservations);

        List<Hotel> recentReservatedHotels = new ArrayList<>();

        for (int i = 0; i < 5 && i < lastReservations.size(); i++) {
            Reservation r = lastReservations.get(i);
            recentReservatedHotels.add(Room.findRoomById(r.room.id).hotel);
        }

        return recentReservatedHotels;
    }

}
