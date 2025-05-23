# Akka Multi-Region Visualizer

## Overview

This app is a distributed, interactive grid visualizer for grid cells, built using:

- **Java backend** (Akka SDK): Handles cell state, updates, and streaming via event-sourced entities and views.
- **HTML/CSS/JS frontend**: Renders a large grid UI, allows user interaction (cell selection, coloring, navigation), and live-updates the grid using HTTP and Server-Sent Events (SSE).

---

## Multi-Region Performance Demonstration

The primary intent of this Akka SDK demo app is to demonstrate multi-region performance. The app uses a single Akka SDK Event-Sourced (ES) entity and a single view that provides queries of the entity state. When this Akka service is deployed in a multi-region environment, the entity data is automatically replicated between regions.

### Data Flow Example

Suppose we have two regions, A and B:

- When an entity instance is created or updated in region A, this triggers a view update in region A. The entity data is then replicated to region B.
- When the entity instance is updated in region B, this triggers an update to the view in region B.

### Visualizing Replication Latency

The grid cells shown in the UI visualize entity state as a color. Each grid cell also displays a number, which is the elapsed milliseconds (ms) between the time the entity was updated and the time when the view row was updated.

In a multi-region environment:

- When an entity is updated in region A, the `updatedAt` field is set using a Java `Instant`.
- When the entity is replicated to region B, the `updatedAt` timestamp is not altered.
- As views are updated in each region, the view update time is computed locally.

This means:

- The view in region A computes the latency between the entity update and the view update in region A.
- When the entity is replicated, the view in region B computes the latency between the update in region A and the view update in region B.

This latency visualization is one of the main features of this demo app, providing insight into cross-region data replication and synchronization in Akka multi-region deployments.

---

## Backend (Java, Akka SDK)

- **Domain Model:**
  - `GridCell` entity with fields like `id`, `status` (red, green, blue, yellow, default), and timestamps.
  - Event-sourced: state changes are tracked as events (e.g., `StatusUpdated`).

- **API Endpoints:**
  - `PUT /grid-cell/update-status`: Update a grid cell’s status.
  - `PUT /grid-cell/span-status`: Update a grid cell’s span status.
  - `PUT /grid-cell/fill-status`: Update a grid cell’s fill status.
  - `PUT /grid-cell/clear-status`: Update a grid cell’s clear status.
  - `PUT /grid-cell/erase-status`: Update a grid cell’s erase status.
  - `PUT /grid-cell/create-predator`: Create a predator grid cell.
  - `GET /grid-cell/list`: Get a list of grid cells.
  - `GET /grid-cell/paginated-list/...`: Get a page of grid cells for a viewport.
  - `GET /grid-cell/stream`: SSE endpoint for streaming grid cell updates.
  - `GET /grid-cell/current-time`: Streams current time (for UI sync).
  - `GET /grid-cell/region`: Get the region of the grid cell.
  - `GET /grid-cell/routes`: Get the routes of the grid cell.

- **Persistence & Query:**
  - Uses Akka’s event sourcing and views to materialize grid cell state and allow efficient queries.
  - Supports paginated and streaming queries for efficient UI updates.

---

## Frontend (HTML/CSS/JS)

- **Grid UI:**
  - Dynamically creates a grid of cells representing grid cells.
  - Each cell can be colored (red, green, blue, yellow) or set to default.
  - Info panel shows region, connection status, and grid summary.

- **User Interaction:**
  - Click/drag to select cells, then use keyboard to set color.
  - Vim-like navigation (e.g., `100x`, `50j`) for moving the viewport.
  - Updates are sent via HTTP PUT to the backend.

- **Live Updates:**
  - Uses SSE (EventSource) to receive real-time updates from the backend and update the grid instantly.
  - Periodically fetches grid cell state for the current viewport.

- **Styling:**
  - Modern, dark-themed CSS with responsive layout and subtle animations.

- **Help Page:**
  - Explains how to use the grid, color cells, and navigate.

---

## Summary

**Purpose:**
This app visualizes a massive, distributed grid of cells, allowing users to interactively update and monitor cell states in real time. It demonstrates multi-region, event-driven architecture using Akka, and provides a highly interactive and responsive UI for managing cell data.

---

## Accessing the UI

When running the app [locally](https://doc.akka.io/java/running-locally.html), you can access the UI at `http://localhost:8080`.

When running the app on an Akka platform, after the app is [deployed](https://doc.akka.io/operations/services/deploy-service.html), create a [route](https://doc.akka.io/operations/services/invoke-service.html). The app UI will be accessible atthe route UR://the-service-route-hostname.

Click the Help link to access the help page.
