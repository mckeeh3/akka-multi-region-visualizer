# Akka Multi-Region Visualizer

## Overview

This app is a distributed, interactive grid visualizer for sensors, built using:

- **Java backend** (Akka SDK): Handles sensor state, updates, and streaming via event-sourced entities and views.
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
  - `Sensor` entity with fields like `id`, `status` (red, green, blue, yellow, default), and timestamps.
  - Event-sourced: state changes are tracked as events (e.g., `StatusUpdated`).

- **API Endpoints:**
  - `PUT /sensor/update-status`: Update a sensor’s status.
  - `GET /sensor/list`: Get a list of sensors.
  - `GET /sensor/paginated-list/...`: Get a page of sensors for a viewport.
  - `GET /sensor/stream`: SSE endpoint for streaming sensor updates.
  - `GET /sensor/current-time`: Streams current time (for UI sync).

- **Persistence & Query:**
  - Uses Akka’s event sourcing and views to materialize sensor state and allow efficient queries.
  - Supports paginated and streaming queries for efficient UI updates.

---

## Frontend (HTML/CSS/JS)

- **Grid UI:**
  - Dynamically creates a grid of cells representing sensors.
  - Each cell can be colored (red, green, blue, yellow) or set to default.
  - Info panel shows region, connection status, and grid summary.

- **User Interaction:**
  - Click/drag to select cells, then use keyboard to set color.
  - Vim-like navigation (e.g., `100x`, `50j`) for moving the viewport.
  - Updates are sent via HTTP PUT to the backend.

- **Live Updates:**
  - Uses SSE (EventSource) to receive real-time updates from the backend and update the grid instantly.
  - Periodically fetches sensor state for the current viewport.

- **Styling:**
  - Modern, dark-themed CSS with responsive layout and subtle animations.

- **Help Page:**
  - Explains how to use the grid, color cells, and navigate.

---

## Summary

**Purpose:**
This app visualizes a massive, distributed grid of sensors, allowing users to interactively update and monitor sensor states in real time. It demonstrates multi-region, event-driven architecture using Akka, and provides a highly interactive and responsive UI for managing sensor data.
