package ca.utoronto.utm.mcs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import dagger.Module;
import dagger.Provides;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

public class PostService implements HttpHandler {

	private MongoClient db;

	@Inject
	public PostService(MongoClient db) {
		this.db = db;
	}

	@Override
	public void handle(HttpExchange r) throws IOException {
		
		
		try {
			
			
			// PUT /api/v1/post
			if (r.getRequestMethod().equals("PUT")) {
				
				// Convert HTTP Request Body to JSON Object
				JSONObject body = new JSONObject(Utils.convert(r.getRequestBody()));
				
				// Check if all required attributes provided
				if (!body.has("title") || !body.has("author") || !body.has("content") || !body.has("tags")) {
					r.sendResponseHeaders(400, -1); // 400 BAD REQUEST 
                	return;
				}
				
				// Parse attributes from JSON Object
				String title = body.getString("title");
                String author = body.getString("author");
                String content = body.getString("content");
                
                JSONArray temp = body.getJSONArray("tags");
				ArrayList<String> tags = new ArrayList<String>();
                for (int i = 0; i < temp.length(); i++) {
                	tags.add(temp.getString(i));
                }
                
                // Create new post with given attributes
				Document post = new Document();
				post.put("title", title);
				post.put("author", author);
				post.put("content", content);
                post.put("tags", tags);
				
				// Insert Document to Collection
				db.getDatabase("csc301a2").getCollection("posts").insertOne(post);
				
				// Write JSON Response with appropriate Status Code
				String response = new JSONObject().put("_id", post.getObjectId("_id")).toString();
				r.sendResponseHeaders(200, response.length()); // 200 OK
				OutputStream output = r.getResponseBody();
                output.write(response.getBytes());
                output.close();
				return;
			}
			
			
			// GET /api/v1/post
			else if (r.getRequestMethod().equals("GET")) {
				
				// Convert HTTP Request Body to JSON Object
				JSONObject body = new JSONObject(Utils.convert(r.getRequestBody()));
				
				
				// Parse _id parameter from JSON Object
				String id = "";
                if (body.has("_id")) id = body.getString("_id");
                
                // Search by _id
                if (!id.isEmpty()) {

    				Document search = new Document("_id",  new ObjectId(id));
    				Document post = db.getDatabase("csc301a2").getCollection("posts").find(search).first();

    				if (post != null) {
    					
    					// Write JSON Response with appropriate Status Code
        				String response = post.toJson().toString();
        				r.sendResponseHeaders(200, response.length()); // 200 OK
        				OutputStream output = r.getResponseBody();
                        output.write(response.getBytes());
                        output.close();
        				return;
    				}
    				
    				r.sendResponseHeaders(404, -1); // 404 NOT FOUND
					return;
                }
                
                
                // Parse title parameter from JSON Object
				String title = "";
                if (body.has("title")) title = body.getString("title");
                
                // Search by title
                if (!title.isEmpty()) {
                	
//                	System.out.println(1);
    				
                	db.getDatabase("csc301a2").getCollection("posts").createIndex(new Document("title", "text"));
    				
//    				System.out.println(2);
    				
    				Document search = new Document("$text", new Document("$search", "\"" + title + "\""));
    				Document projection = new Document("score", new Document("$meta", "textScore"));
    				Document sort = new Document("score", new Document("$meta", "textScore"));
    				
//    				System.out.println(3);
    				
    				MongoCursor<Document> posts = db.getDatabase("csc301a2").getCollection("posts").find(Filters.regex("title", title)).cursor();
		
//    				System.out.println(4);
    				
    				if (!posts.hasNext()) {
    					r.sendResponseHeaders(404, -1); // 404 NOT FOUND 
                    	return;
    				}
    				
    				
    				ArrayList<String> postList = new ArrayList<String>();
    				
    				
    				while (posts.hasNext()) {
    					postList.add(posts.next().toJson());
    				}
    				
    				// Write JSON Response with appropriate Status Code
    				String response = postList.toString();
    				r.sendResponseHeaders(200, response.length()); // 200 OK
    				OutputStream output = r.getResponseBody();
                    output.write(response.getBytes());
                    output.close();
    				return;
                }
                
                
                // If no _id and no title
                else {
                	r.sendResponseHeaders(400, -1); // 400 BAD REQUEST 
                	return;
                }
			}
			
			
			// DELETE /api/v1/post
			else if (r.getRequestMethod().equals("DELETE")) {

				// Convert HTTP Request Body to JSON Object
				JSONObject body = new JSONObject(Utils.convert(r.getRequestBody()));
				
				// Parse _id parameter from JSON Object
				String id = "";
                if (body.has("_id")) id = body.getString("_id");
              
                // Check if all required parameters provided
                if (id.isEmpty()) {
                	r.sendResponseHeaders(400, -1); // 400 BAD REQUEST 
                	return;
                }
				
				// Create new Search query with given _id
				Document search = new Document("_id",  new ObjectId(id));
			
				// Delete post if post exists
				if (db.getDatabase("csc301a2").getCollection("posts").findOneAndDelete(search) != null) {
					r.sendResponseHeaders(200, -1); // 200 OK
					return;
				};
				
				// If post does not exist
				r.sendResponseHeaders(404, -1); // 404 NOT FOUND
				return;
			}
			
			
			// If method other than PUT, GET or DELETE
			else {
				r.sendResponseHeaders(405, -1); // 405 METHOD NOT ALLOWED
				return;
			}
		}
		
		
		// JSON Error (improper/missing format)
		catch (JSONException e) {
        	r.sendResponseHeaders(400, -1); // 400 BAD REQUEST
            e.printStackTrace();
            return;
        }
    	
		
		// Server Error (unsuccessful add/delete)
    	catch (IOException e) {
        	r.sendResponseHeaders(500, -1); // 500 INTERNAL SERVER ERROR
            e.printStackTrace();
            return;
        }	
	}
}