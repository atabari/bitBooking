

package controllers;

import helpers.Authenticators;
import models.AppUser;
import models.Comment;
import models.Hotel;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;


public class Comments extends Controller {

    private static final Form<Comment> commentForm = Form.form(Comment.class);

    /*Allows buyers to add comment and rating for hotel */

    @Security.Authenticated(Authenticators.BuyerFilter.class)
    public Result insertComment(Integer hotelId) {
        Form<Comment> boundForm = commentForm.bindFromRequest();

        /*Taking values from input fields*/
        Comment comment = boundForm.get();

        /*Connecting comment with hotel, and user*/
        comment.hotel = Hotel.findHotelById(hotelId);
        comment.user =  AppUser.getUserByEmail(request().cookies().get("email").value());

        comment.save();
        return redirect(routes.Hotels.showHotel(comment.hotel.id));
    }
    /*Allowing admin to delete comment*/
    @Security.Authenticated(Authenticators.AdminFilter.class)
    public Result deleteComment(Integer id) {
        Comment comment = Comment.findCommentById(id);
        comment.delete();

        return redirect(routes.Hotels.showHotel(comment.hotel.id));
    }

}
