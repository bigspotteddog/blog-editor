package com.example.blogeditor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHContentUpdateResponse;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController()
@RequestMapping("/blog")
public class BlogController {
  private static final Logger log = Logger.getLogger(BlogController.class.getName());

  @GetMapping(value = "files", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public HashMap<String, Object> get() throws IOException {
    String token = System.getenv("GITHUB_ACCESS_TOKEN");
    GitHub github = GitHub.connectUsingOAuth(token);

    GHRepository repository = github.getRepository("throwaway95857209/blog");

    GHContent dataContent = repository.getFileContent("docs/data/data.json");
    HashMap<String, Object> dataMap = new ObjectMapper().readValue(dataContent.read(),
        new TypeReference<HashMap<String, Object>>() {
        });

    return dataMap;
  }

  @GetMapping(value = "files/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String getFile(@PathVariable("name") String name) throws IOException {
    String token = System.getenv("GITHUB_ACCESS_TOKEN");
    GitHub github = GitHub.connectUsingOAuth(token);
    GHRepository repository = github.getRepository("throwaway95857209/blog");

    GHContent content = repository.getFileContent("docs/content/${name}.json".replace("${name}", name));
    if (content != null) {
      try (Scanner s = new Scanner(content.read()).useDelimiter("\\A")) {
        return s.hasNext() ? s.next() : "{}";
      }
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "entity not found");
  }

  @GetMapping("close")
  public void close() {
    System.exit(0);
  }

  @PostMapping("posts")
  public BlogPost post(@RequestBody BlogPost blogPost) throws IOException {
    String token = System.getenv("GITHUB_ACCESS_TOKEN");
    GitHub github = GitHub.connectUsingOAuth(token);

    GHRepository repository = github.getRepository("throwaway95857209/blog");

    String name = getName(blogPost.getTitle());
    String message = getMessage(name);

    Map<String, Object> post = createOrUpdatePost(blogPost, repository, name, message);

    updateData(repository, message, post);

    blogPost.setName(name);
    return blogPost;
  }

  private String getMessage(String name) {
    return "Add/update blog post ${name}".replace("${name}", name);
  }

  private String getName(String title) {
    return title.replaceAll("[^a-zA-Z0-9]", "-").toLowerCase();
  }

  private Map<String, Object> createOrUpdatePost(BlogPost blogPost, GHRepository repository, String name,
      String message)
      throws IOException {
    Map<String, Object> post = new LinkedHashMap<>();
    post.put("name", name);
    post.put("title", blogPost.getTitle());
    post.put("subtitle", blogPost.getSubtitle());
    post.put("author", blogPost.getAuthor());
    post.put("created", new Date().getTime());
    post.put("content", blogPost.getContent());
    post.put("properties", blogPost.getProperties());

    String path = "docs/content/" + name + ".json";
    post.put("path", path.substring(5));
    String value = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(post);

    try {
      GHContent fileContent = repository.getFileContent(path);
      fileContent.update(value, message, "main");
    } catch (GHFileNotFoundException e) {
      repository.createContent().path(path).content(value).message(message).commit();
    }
    return post;
  }

  private void updateData(GHRepository repository, String message, Map<String, Object> post)
      throws IOException, JsonProcessingException {
    try {
      updateDataMap(repository, message, post);
    } catch (FileNotFoundException fnfe) {
      createDataMap(repository, message, post);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void createDataMap(GHRepository repository, String message, Map<String, Object> post)
      throws IOException, JsonProcessingException {
    Map<String, Object> dataMap = new LinkedHashMap<>();
    Map<String, Object> postMap = new LinkedHashMap<>();
    dataMap.put("posts", postMap);

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.putAll(post);
    summary.remove("content");
    postMap.put((String) post.get("name"), summary);

    repository.createContent().path("docs/data/data.json")
        .content(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(dataMap))
        .message(message).commit();
  }

  private void updateDataMap(GHRepository repository, String message, Map<String, Object> post)
      throws IOException, StreamReadException, DatabindException, JsonProcessingException {
    GHContent dataContent = repository.getFileContent("docs/data/data.json");
    HashMap<String, Object> dataMap = new ObjectMapper().readValue(dataContent.read(),
        new TypeReference<HashMap<String, Object>>() {
        });
    @SuppressWarnings("unchecked")
    Map<String, Object> postMap = (Map<String, Object>) dataMap.get("posts");

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.putAll(post);
    summary.remove("content");
    postMap.put((String) post.get("name"), summary);

    dataContent.update(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(dataMap), message,
        "main");
  }

  @PostMapping("uploadImage")
  public ResponseEntity<String> handleImageUpload(@RequestPart("image") MultipartFile imageFile,
      @RequestPart("path") String path) {
    if (!imageFile.isEmpty()) {
      try {
        uploadImageToRepository(imageFile, path);
        return ResponseEntity.status(HttpStatus.OK).body("Image uploaded successfully to GitHub!");
      } catch (IOException e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Failed to upload image: " + e.getMessage());
      }
    } else {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Image file is empty");
    }
  }

  public void uploadImageToRepository(MultipartFile imageFile, String path) throws IOException {
    String token = System.getenv("GITHUB_ACCESS_TOKEN");
    GitHub github = GitHub.connectUsingOAuth(token);

    // Get repository
    GHRepository repository = github.getRepository("throwaway95857209/blog");

    // Upload image file
    String filename = path + "/" + imageFile.getOriginalFilename();
    byte[] bytes = imageFile.getBytes();
    GHContentUpdateResponse commit = repository.createContent().path(filename).content(bytes)
        .message("Upload ${filename}.".replace("${filename}", filename)).commit();
  }
}
