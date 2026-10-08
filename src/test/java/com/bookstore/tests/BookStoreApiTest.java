package com.bookstore.tests;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class BookStoreApiTest {

    private static final String BASE_URL = "https://bookstore.toolsqa.com";
    private static final String ISBN_ONE = "9781449365035";   // Speaking JavaScript
    private static final String ISBN_TWO = "9781449325862";   // Git Pocket Guide

    // Data shared between tests
    private String userName;
    private final String password = "Geetha@22";
    private String userId;
    private String token;

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = BASE_URL;
        userName = "geethaaug" + System.currentTimeMillis();   // unique each run
        System.out.println("==================================================");
        System.out.println("BookStore API tests started. Base URL: " + BASE_URL);
        System.out.println("Test user name for this run: " + userName);
        System.out.println("==================================================");
    }

    // ---------- helpers ----------
    private String credentialsBody() {
        return String.format("{\"userName\":\"%s\",\"password\":\"%s\"}", userName, password);
    }

    private String authHeader() {
        return "Bearer " + token;
    }

    private void start(String testName, String description) {
        System.out.println("\n--------------------------------------------------");
        System.out.println("RUNNING : " + testName);
        System.out.println("PURPOSE : " + description);
    }

    private void pass(String message) {
        System.out.println("PASSED  : " + message);
    }

    // ================= a) REGISTER USER =================
    @Test(priority = 1)
    public void a1_registerUser_success() {
        start("a1_registerUser_success", "POST /Account/v1/User with a new username and password");

        Response response = given()
                .contentType(ContentType.JSON)
                .body(credentialsBody())
                .when()
                .post("/Account/v1/User")
                .then()
                .statusCode(201)
                .header("Content-Type", containsString("application/json"))
                .body("username", equalTo(userName))
                .body("userID", notNullValue())
                .body("books", empty())
                .extract().response();

        userId = response.jsonPath().getString("userID");
        pass("User registered. Status 201, userID = " + userId + ", books list is empty");
    }

    @Test(priority = 2, dependsOnMethods = "a1_registerUser_success")
    public void a2_registerUser_alreadyExists() {
        start("a2_registerUser_alreadyExists", "POST /Account/v1/User again with the same username");

        given()
                .contentType(ContentType.JSON)
                .body(credentialsBody())
                .when()
                .post("/Account/v1/User")
                .then()
                .statusCode(406)
                .body("code", equalTo("1204"))
                .body("message", equalTo("User exists!"));

        pass("Duplicate user rejected. Status 406, code 1204, message 'User exists!'");
    }

    // ================= b) GENERATE TOKEN =================
    @Test(priority = 3, dependsOnMethods = "a1_registerUser_success")
    public void b1_generateToken_success() {
        start("b1_generateToken_success", "POST /Account/v1/GenerateToken with valid credentials");

        Response response = given()
                .contentType(ContentType.JSON)
                .body(credentialsBody())
                .when()
                .post("/Account/v1/GenerateToken")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("application/json"))
                .body("status", equalTo("Success"))
                .body("result", equalTo("User authorized successfully."))
                .body("token", notNullValue())
                .extract().response();

        token = response.jsonPath().getString("token");
        Assert.assertNotNull(token, "Token should not be null");
        pass("Token generated. Status 200, status 'Success', token starts with "
                + token.substring(0, 15) + "...");
    }

    @Test(priority = 4)
    public void b2_generateToken_wrongPassword() {
        start("b2_generateToken_wrongPassword", "POST /Account/v1/GenerateToken with a wrong password");

        String badBody = String.format("{\"userName\":\"%s\",\"password\":\"%s\"}", userName, "Wrong@123");

        given()
                .contentType(ContentType.JSON)
                .body(badBody)
                .when()
                .post("/Account/v1/GenerateToken")
                .then()
                // Document says 400; the live API sometimes returns 200 with status "Failed"
                .statusCode(anyOf(is(200), is(400)))
                .body("status", equalTo("Failed"))
                .body("token", nullValue())
                .body("result", equalTo("User authorization failed."));

        pass("Wrong password rejected. status 'Failed', token is null, result 'User authorization failed.'");
    }

    // ================= c) GET ALL BOOKS =================
    @Test(priority = 5)
    public void c_getAllBooks() {
        start("c_getAllBooks", "GET /BookStore/v1/Books");

        Response response = given()
                .when()
                .get("/BookStore/v1/Books")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("application/json"))
                .body("books", not(empty()))
                .body("books.isbn", hasItems(ISBN_ONE, ISBN_TWO))
                .extract().response();

        int count = response.jsonPath().getList("books").size();
        pass("Got all books. Status 200, total books = " + count + ", expected ISBNs are present");
    }

    // ================= d) GET BOOK BY ISBN =================
    @Test(priority = 6)
    public void d1_getBookByIsbn_success() {
        start("d1_getBookByIsbn_success", "GET /BookStore/v1/Book?ISBN=" + ISBN_ONE);

        given()
                .queryParam("ISBN", ISBN_ONE)
                .when()
                .get("/BookStore/v1/Book")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("application/json"))
                .body("isbn", equalTo(ISBN_ONE))
                .body("title", equalTo("Speaking JavaScript"))
                .body("author", equalTo("Axel Rauschmayer"))
                .body("pages", equalTo(460));

        pass("Book found. Status 200, title 'Speaking JavaScript', author 'Axel Rauschmayer', pages 460");
    }

    @Test(priority = 7)
    public void d2_getBookByIsbn_invalidIsbn() {
        start("d2_getBookByIsbn_invalidIsbn", "GET /BookStore/v1/Book?ISBN=0000000000000 (does not exist)");

        given()
                .queryParam("ISBN", "0000000000000")
                .when()
                .get("/BookStore/v1/Book")
                .then()
                .statusCode(400)
                .body("code", equalTo("1205"))
                .body("message", equalTo("ISBN supplied is not available in Books Collection!"));

        pass("Invalid ISBN rejected. Status 400, code 1205");
    }

    // ================= e) ADD BOOK TO USER COLLECTION =================
    @Test(priority = 8, dependsOnMethods = "b1_generateToken_success")
    public void e1_addBook_success() {
        start("e1_addBook_success", "POST /BookStore/v1/Books with token, adding ISBN " + ISBN_ONE);

        String body = String.format(
                "{\"userId\":\"%s\",\"collectionOfIsbns\":[{\"isbn\":\"%s\"}]}", userId, ISBN_ONE);

        given()
                .header("Authorization", authHeader())
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/BookStore/v1/Books")
                .then()
                .statusCode(201)
                .header("Content-Type", containsString("application/json"))
                .body("books[0].isbn", equalTo(ISBN_ONE));

        pass("Book added to user's collection. Status 201, isbn = " + ISBN_ONE);
    }

    @Test(priority = 9, dependsOnMethods = "a1_registerUser_success")
    public void e2_addBook_withoutAuthorization() {
        start("e2_addBook_withoutAuthorization", "POST /BookStore/v1/Books without the Authorization header");

        String body = String.format(
                "{\"userId\":\"%s\",\"collectionOfIsbns\":[{\"isbn\":\"%s\"}]}", userId, ISBN_TWO);

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/BookStore/v1/Books")
                .then()
                .statusCode(401)
                .body("code", equalTo("1200"))
                .body("message", equalTo("User not authorized!"));

        pass("Request blocked without token. Status 401, code 1200, message 'User not authorized!'");
    }

    // ================= g) GET USER DETAILS (optional) =================
    @Test(priority = 10, dependsOnMethods = "e1_addBook_success")
    public void g_getUserDetails() {
        start("g_getUserDetails", "GET /Account/v1/User/{userId} to confirm the book was added");

        given()
                .header("Authorization", authHeader())
                .when()
                .get("/Account/v1/User/" + userId)
                .then()
                .statusCode(200)
                .body("userId", equalTo(userId))
                .body("username", equalTo(userName))
                .body("books[0].isbn", equalTo(ISBN_ONE));

        pass("User details match. Status 200, username = " + userName + ", book " + ISBN_ONE + " is in the collection");
    }

    // ================= f) UPDATE BOOK (PUT) =================
    @Test(priority = 11, dependsOnMethods = "e1_addBook_success")
    public void f1_updateBook_success() {
        start("f1_updateBook_success", "PUT /BookStore/v1/Books/" + ISBN_ONE + " replacing it with " + ISBN_TWO);

        String body = String.format("{\"userId\":\"%s\",\"isbn\":\"%s\"}", userId, ISBN_TWO);

        given()
                .header("Authorization", authHeader())
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .put("/BookStore/v1/Books/" + ISBN_ONE)   // old ISBN in the path
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("application/json"))
                .body("userId", equalTo(userId))
                .body("username", equalTo(userName))
                .body("books[0].isbn", equalTo(ISBN_TWO));

        pass("Book updated. Status 200, collection now holds ISBN " + ISBN_TWO);
    }

    @Test(priority = 12, dependsOnMethods = "f1_updateBook_success")
    public void f2_updateBook_isbnNotInCollection() {
        start("f2_updateBook_isbnNotInCollection", "PUT /BookStore/v1/Books/0000000000000 (not in user's collection)");

        String body = String.format("{\"userId\":\"%s\",\"isbn\":\"%s\"}", userId, ISBN_TWO);

        given()
                .header("Authorization", authHeader())
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .put("/BookStore/v1/Books/0000000000000")
                .then()
                .statusCode(400)
                .body("code", equalTo("1206"))
                .body("message", equalTo("ISBN supplied is not available in User's Collection!"));

        pass("Update rejected for unknown ISBN. Status 400, code 1206");
    }

    @Test(priority = 13, dependsOnMethods = "f1_updateBook_success")
    public void f3_updateBook_withoutAuthorization() {
        start("f3_updateBook_withoutAuthorization", "PUT /BookStore/v1/Books/" + ISBN_TWO + " without the Authorization header");

        String body = String.format("{\"userId\":\"%s\",\"isbn\":\"%s\"}", userId, ISBN_ONE);

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .put("/BookStore/v1/Books/" + ISBN_TWO)
                .then()
                .statusCode(401)
                .body("code", equalTo("1200"))
                .body("message", equalTo("User not authorized!"));

        pass("Update blocked without token. Status 401, code 1200");
    }

    // ================= h) DELETE BOOK =================
    @Test(priority = 14, dependsOnMethods = "f1_updateBook_success")
    public void h1_deleteBook_withoutAuthorization() {
        start("h1_deleteBook_withoutAuthorization", "DELETE /BookStore/v1/Book without the Authorization header");

        String body = String.format("{\"isbn\":\"%s\",\"userId\":\"%s\"}", ISBN_TWO, userId);

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .delete("/BookStore/v1/Book")
                .then()
                .statusCode(401)
                .body("code", equalTo("1200"))
                .body("message", equalTo("User not authorized!"));

        pass("Delete blocked without token. Status 401, code 1200");
    }

    @Test(priority = 15, dependsOnMethods = "h1_deleteBook_withoutAuthorization")
    public void h2_deleteBook_success() {
        start("h2_deleteBook_success", "DELETE /BookStore/v1/Book with token, removing ISBN " + ISBN_TWO);

        String body = String.format("{\"isbn\":\"%s\",\"userId\":\"%s\"}", ISBN_TWO, userId);

        given()
                .header("Authorization", authHeader())
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .delete("/BookStore/v1/Book")
                .then()
                .statusCode(204)
                .body(emptyOrNullString());

        pass("Book deleted. Status 204 and the response body is empty");
    }

    @Test(priority = 16, dependsOnMethods = "h2_deleteBook_success")
    public void h3_deleteBook_notInUserCollection() {
        start("h3_deleteBook_notInUserCollection", "DELETE /BookStore/v1/Book for an ISBN already removed");

        String body = String.format("{\"isbn\":\"%s\",\"userId\":\"%s\"}", ISBN_TWO, userId);

        given()
                .header("Authorization", authHeader())
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .delete("/BookStore/v1/Book")
                .then()
                .statusCode(400)
                .body("code", equalTo("1206"))
                .body("message", equalTo("ISBN supplied is not available in User's Collection!"));

        pass("Second delete rejected. Status 400, code 1206");
    }
}