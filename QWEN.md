# Blog Editor Project

## Project Overview

This is a Spring Boot web application designed as a blog editor that integrates with GitHub. The application allows users to create, edit, and publish blog posts to a GitHub repository via the GitHub API. It acts as a bridge between the user interface and GitHub, enabling content management directly from the editor tool.

The project uses the following key technologies and components:
- Spring Boot 3.2.5 as the web framework
- Java 21 as the development language
- GitHub API library for repository interaction
- Maven as the build tool

## Architecture

The application has two main functional areas:

1. **Blog Management**: Handles creating, updating, and retrieving blog posts stored in a GitHub repository
2. **HTTP Proxy Functionality**: Acts as a proxy server to serve web content, particularly for the editor interface

Key components:
- `BlogController`: Manages blog post operations including CRUD operations with GitHub
- `BlogPost`: Data model for blog posts with title, subtitle, author, content, and properties
- `HttpRequestController`: Acts as a proxy server to serve web content
- `DependencyManager`: Handles URL rewriting and dependency management for proxied content
- `UrlReplacer`: Replaces URLs in HTML/CSS/JS for proxy compatibility

## Building and Running

### Prerequisites
- Java 21
- Maven 3.6+
- GitHub Personal Access Token with appropriate repository permissions

### Build Instructions
```bash
# Build the project
./mvnw clean package

# Or run directly
./mvnw spring-boot:run
```

### Running the Application
1. Set up the required environment variable:
   ```bash
   export GITHUB_ACCESS_TOKEN=<your_github_token>
   ```

2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

3. The application will start on port 8888 by default (configurable in `application.properties`)

### Configuration
The application can be configured through `application.properties`:
- `server.port`: Sets the server port (default: 8888)
- `spring.servlet.multipart.max-file-size`: Max file size for uploads (default: 10MB)
- `spring.servlet.multipart.max-request-size`: Max request size for uploads (default: 10MB)

## Key Features

### Blog Post Management
- **Create/Update Posts**: Send POST requests to `/blog/posts` with a BlogPost JSON payload
- **Retrieve Posts**: GET requests to `/blog/files` and `/blog/files/{name}` to fetch blog data
- **Image Upload**: POST requests to `/blog/uploadImage` for image uploads to GitHub repository

### GitHub Integration
- Automatically stores blog posts as JSON files in `docs/content/` directory
- Maintains an index of all posts in `docs/data/data.json`
- Uses GitHub OAuth for repository access
- Commits changes to the `main` branch

### Proxy Functionality
- Serves as a reverse proxy for web content
- Rewrites URLs in HTML, CSS, and JavaScript to work with the proxy
- Handles cookies and request headers appropriately
- Supports CORS for cross-origin requests

## Endpoints

### Blog Endpoints
- `GET /blog/files` - Retrieve all blog posts data
- `GET /blog/files/{name}` - Retrieve specific blog post by name
- `POST /blog/posts` - Create or update a blog post
- `POST /blog/uploadImage` - Upload image files to GitHub repository
- `GET /blog/close` - Close endpoint (currently commented out)

### Proxy Endpoints
- `GET /**` - Serves proxied content with URL rewriting

## User Interface

The main user interface is located in `src/main/resources/static/index.html`. This file contains:

- A dual-panel layout with an editor frame on the left and component selector/styles panel on the right
- A toolbar with options for adding different Bootstrap components (containers, rows, columns, headings, etc.)
- A component selector that shows the DOM hierarchy of the editing frame
- A styles editor with Monaco editor integration for CSS, HTML, and JavaScript editing
- Support for editing HTML content in place with double-click functionality
- Visual highlighting of elements as the user hovers over them
- Copy, cut, paste, and delete functionality for elements
- Support for adding and editing attributes, classes, and inline styles

The UI is designed to work as a visual editor for web content, with the editing frame loading external content through the proxy functionality.

## Development Conventions

- Java 21 is used as the target runtime
- Maven is used for dependency management and building
- GitHub OAuth token is expected as an environment variable
- Repository structure: `docs/content/` for individual posts, `docs/data/data.json` for index
- Post names are automatically generated from titles by replacing non-alphanumeric characters with hyphens

## Repository Integration

The application is configured to work with the repository `throwaway95857209/blog` by default, though this can be modified in the code. It manages blog content in the following structure:
- `docs/content/{post-name}.json` - Individual blog posts
- `docs/data/data.json` - Index of all available posts

## Testing

Unit tests can be run with:
```bash
./mvnw test
```

The project includes Spring Boot Starter Test for testing support.