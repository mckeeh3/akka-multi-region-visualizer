document.addEventListener('DOMContentLoaded', () => {
  // --- Configuration ---
  // Grid dimensions will be calculated dynamically based on viewport size
  let gridRows = 0; // Will be calculated dynamically
  let gridCols = 0; // Will be calculated dynamically
  const cellMinSize = 30; // Minimum cell size in pixels (increased from 20)

  // Viewport configuration
  const MIN_GRID_COORD = -1000000; // Minimum grid coordinate
  const MAX_GRID_COORD = 1000000; // Maximum grid coordinate
  let viewportX = 0; // Current X offset of the viewport (horizontal)
  let viewportY = 0; // Current Y offset of the viewport (vertical)

  // Use the current origin for API calls and SSE stream
  const origin = window.location.origin; // Gets the protocol, hostname, and port
  const viewStreamUrl = `${origin}/sensor/stream`; // SSE URL
  const viewListUrl = `${origin}/sensor/list`; // SSE URL

  // --- State ---
  let hoveredCellId = null; // ID of the currently hovered cell ('cell-R-C')
  let eventSource = null; // EventSource instance
  let sensorListInterval = null; // Interval timer for fetching sensor list

  // Selection state
  let selectionMode = false;
  let selectionStart = null;
  let selectionEnd = null;
  let currentSelection = []; // Array of selected cell IDs

  // Cell count tracking
  let cellCounts = {
    total: 0,
    red: 0,
    green: 0,
    blue: 0,
    yellow: 0,
  };

  // --- DOM References ---
  const gridContainer = document.getElementById('grid-container');
  const leftAxis = document.getElementById('left-axis');
  const bottomAxis = document.getElementById('bottom-axis');
  const regionUrlSpan = document.getElementById('region-url');
  const connectionStatusSpan = document.getElementById('connection-status');
  const gridSummary = document.getElementById('grid-summary');

  // --- Functions ---

  /**
   * Updates the grid summary display with current cell counts
   */
  function updateGridSummary() {
    gridSummary.textContent = `Total: ${cellCounts.total}, R: ${cellCounts.red}, G: ${cellCounts.green}, B: ${cellCounts.blue}, Y: ${cellCounts.yellow}`;
  }

  /**
   * Updates the cell counts when a cell status changes
   * @param {string} oldStatus Previous cell status
   * @param {string} newStatus New cell status
   */
  function updateCellCounts(oldStatus, newStatus) {
    // Decrement the old status count if it was active
    if (oldStatus !== 'default') {
      cellCounts[oldStatus]--;
      cellCounts.total--;
    }

    // Increment the new status count if it's active
    if (newStatus !== 'default') {
      cellCounts[newStatus]++;
      cellCounts.total++;
    }
  }

  /**
   * Gets the current status of a cell from its classes
   * @param {HTMLElement} cellElement The cell element
   * @returns {string} The cell status ('red', 'green', 'blue', 'yellow', or 'default')
   */
  function getCellStatus(cellElement) {
    if (cellElement.classList.contains('cell-red')) return 'red';
    if (cellElement.classList.contains('cell-green')) return 'green';
    if (cellElement.classList.contains('cell-blue')) return 'blue';
    if (cellElement.classList.contains('cell-yellow')) return 'yellow';
    return 'default';
  }

  /**
   * Updates the connection status display.
   * @param {string} statusText Text to display
   * @param {string} cssClass Class ('connected', 'error', '')
   */
  function updateConnectionStatus(statusText, cssClass = '') {
    connectionStatusSpan.textContent = statusText;
    connectionStatusSpan.className = cssClass;
  }

  /**
   * Calculates the optimal number of rows and columns to fill the available space
   * based on the current viewport dimensions and minimum cell size.
   */
  function calculateGridDimensions() {
    // Get the info panel height for top spacing reference
    const infoPanelHeight = document.getElementById('info-panel').offsetHeight;

    // Calculate available space (accounting for padding and margins)
    // Use larger side margins to reduce the number of columns
    const sideMargin = infoPanelHeight * 2; // Doubled side margin
    const availableWidth = window.innerWidth - 2 * sideMargin; // Double margin on both sides
    const availableHeight = window.innerHeight - infoPanelHeight - 2 * sideMargin; // Top panel + bottom margin

    // Calculate number of cells that can fit (accounting for 3px gap between cells)
    gridCols = Math.max(1, Math.floor(availableWidth / (cellMinSize + 3)));
    gridRows = Math.max(1, Math.floor(availableHeight / (cellMinSize + 3)));

    console.log(`Calculated grid dimensions: ${gridRows}x${gridCols} based on viewport ${window.innerWidth}x${window.innerHeight}`);
  }

  /**
   * Creates the left axis with tick marks and labels
   */
  function createLeftAxis() {
    leftAxis.innerHTML = ''; // Clear existing axis

    // Calculate exact cell dimensions from the grid
    const gridComputedStyle = window.getComputedStyle(gridContainer);
    const gridContentHeight = gridContainer.clientHeight - parseInt(gridComputedStyle.paddingTop, 10) - parseInt(gridComputedStyle.paddingBottom, 10);

    // Get the exact cell height including gap
    const totalGapHeight = (gridRows - 1) * 3; // 3px gap between cells
    const cellHeight = (gridContentHeight - totalGapHeight) / gridRows;

    // Get grid container's computed style
    const gridStyle = window.getComputedStyle(gridContainer);
    const topPadding = parseInt(gridStyle.paddingTop, 10) || 0;
    const borderWidth = parseInt(gridStyle.borderTopWidth, 10) || 0;
    const gapSize = 3; // Match the gap size from CSS

    // Create ticks and labels for each row
    for (let r = 0; r < gridRows; r++) {
      // Create tick mark
      const tick = document.createElement('div');
      tick.classList.add('axis-tick', 'left-tick');

      // Add special classes for multiples of 5 and 10
      if (r % 10 === 0) {
        tick.classList.add('tick-10');

        // Add label for multiples of 10
        const label = document.createElement('div');
        label.classList.add('axis-label', 'left-label');
        label.textContent = r;
        // Position label at the center of the cell
        const cellStart = topPadding + borderWidth + r * cellHeight + (r > 0 ? r * 3 : 0);
        const labelPosition = cellStart + cellHeight / 2;
        label.style.top = `${labelPosition}px`;
        leftAxis.appendChild(label);
      } else if (r % 5 === 0) {
        tick.classList.add('tick-5');
      }

      // Create a tick for each cell boundary
      let tickPosition;
      if (r === 0) {
        // First tick at the top edge of the first cell
        tickPosition = topPadding + borderWidth;
      } else {
        // Other ticks at the boundaries between cells (accounting for gaps)
        tickPosition = topPadding + borderWidth + r * cellHeight + (r - 0.5) * 3;
      }
      tick.style.top = `${tickPosition}px`;
      leftAxis.appendChild(tick);
    }
  }

  /**
   * Creates the bottom axis with tick marks and labels
   */
  function createBottomAxis() {
    bottomAxis.innerHTML = ''; // Clear existing axis

    // Calculate exact cell dimensions from the grid
    const gridComputedStyle = window.getComputedStyle(gridContainer);
    const gridContentWidth = gridContainer.clientWidth - parseInt(gridComputedStyle.paddingLeft, 10) - parseInt(gridComputedStyle.paddingRight, 10);

    // Get the exact cell width including gap
    const totalGapWidth = (gridCols - 1) * 3; // 3px gap between cells
    const cellWidth = (gridContentWidth - totalGapWidth) / gridCols;

    // Get grid container's computed style
    const gridStyle = window.getComputedStyle(gridContainer);
    const leftPadding = parseInt(gridStyle.paddingLeft, 10) || 10;
    const borderWidth = parseInt(gridStyle.borderLeftWidth, 10) || 0;
    const gapSize = 3; // Match the gap size from CSS

    // Create ticks and labels for each column
    for (let c = 0; c < gridCols; c++) {
      // Create tick mark
      const tick = document.createElement('div');
      tick.classList.add('axis-tick', 'bottom-tick');

      // Add special classes for multiples of 5 and 10
      if (c % 10 === 0) {
        tick.classList.add('tick-10');

        // Add label for multiples of 10
        const label = document.createElement('div');
        label.classList.add('axis-label', 'bottom-label');
        label.textContent = c;
        // Position label at the center of the cell
        const cellStart = leftPadding + borderWidth + c * cellWidth + (c > 0 ? c * 3 : 0);
        const labelPosition = cellStart + cellWidth / 2;
        label.style.left = `${labelPosition}px`;
        bottomAxis.appendChild(label);
      } else if (c % 5 === 0) {
        tick.classList.add('tick-5');
      }

      // Create a tick for each cell boundary
      let tickPosition;
      if (c === 0) {
        // First tick at the left edge of the first cell
        tickPosition = leftPadding + borderWidth;
      } else {
        // Other ticks at the boundaries between cells (accounting for gaps)
        tickPosition = leftPadding + borderWidth + c * cellWidth + (c - 0.5) * 3;
      }
      tick.style.left = `${tickPosition}px`;
      bottomAxis.appendChild(tick);
    }
  }

  /**
   * Generates the grid cells dynamically.
   */
  function createGrid() {
    // Calculate grid dimensions based on current viewport
    calculateGridDimensions();

    // Clear existing grid
    gridContainer.innerHTML = '';

    // Create grid cells
    for (let row = 0; row < gridRows; row++) {
      for (let col = 0; col < gridCols; col++) {
        const cell = document.createElement('div');
        cell.className = 'grid-cell';

        // Calculate the actual grid coordinates based on viewport position
        const actualRow = row + viewportY;
        const actualCol = col + viewportX;
        // Use 'x' as separator between row and column to avoid issues with negative numbers
        cell.id = `cell-${actualRow}x${actualCol}`;

        // Add hover tracking for keyboard shortcuts
        cell.addEventListener('mouseenter', () => {
          hoveredCellId = cell.id;
          if (selectionMode && selectionStart) {
            selectionEnd = getCellCoordinates(cell.id);
            updateSelectionPreview();
          }
        });

        cell.addEventListener('mouseleave', () => {
          if (hoveredCellId === cell.id) {
            hoveredCellId = null;
          }
        });

        // Add mouse events for selection
        cell.addEventListener('mousedown', (event) => {
          if (selectionMode) {
            event.preventDefault();
            selectionStart = getCellCoordinates(cell.id);
            selectionEnd = selectionStart;
            updateSelectionPreview();
          }
        });

        cell.addEventListener('mouseup', (event) => {
          if (selectionMode && selectionStart) {
            event.preventDefault();
            selectionEnd = getCellCoordinates(cell.id);
            finalizeSelection();
          }
        });

        gridContainer.appendChild(cell);
      }
    }
    console.log(`Grid created with ${gridRows}x${gridCols} cells.`);

    gridContainer.style.gridTemplateColumns = `repeat(${gridCols}, minmax(${cellMinSize}px, 1fr))`;
    gridContainer.style.gridTemplateRows = `repeat(${gridRows}, minmax(${cellMinSize}px, 1fr))`;

    // Reset cell counts
    cellCounts = {
      total: 0,
      red: 0,
      green: 0,
      blue: 0,
      yellow: 0,
    };

    // Update the grid summary display
    updateGridSummary();

    // Create axes after grid is populated
    setTimeout(() => {
      createLeftAxis();
      createBottomAxis();
    }, 0);
  }

  /**
   * Sends the update command to the backend via HTTP PUT.
   * @param {string} id The cell's entity ID (e.g., "RxC" format)
   * @param {string} action The action key ('r', 'g', 'b', 'd')
   */
  async function sendCellUpdate(id, action) {
    // Use 'rxc' as the service ID format (no conversion needed)
    const serverFormatId = id;

    const apiUrl = `${origin}/sensor/update-status`;
    // Get current time in ISO8601 format
    const updatedAt = new Date().toISOString();
    const statusMap = { r: 'red', g: 'green', b: 'blue', y: 'yellow', d: 'default' };
    const status = statusMap[action];

    console.log(`Sending PUT to ${apiUrl} with id: ${serverFormatId}, status: ${status}, updatedAt: ${updatedAt}`);

    try {
      const response = await fetch(apiUrl, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          id: serverFormatId,
          status: status,
          updatedAt: updatedAt,
        }),
      });

      if (!response.ok) {
        console.error(`HTTP error! Status: ${response.status}`, await response.text());
      } else {
        console.log(`Update request for ${id} sent successfully.`);
        // Note: Visual update happens via WebSocket stream, not here.
      }
    } catch (error) {
      console.error('Error sending cell update:', error);
    }
  }

  /**
   * Handles incoming messages from the SSE stream.
   * @param {string} messageData Raw message data string (expected JSON)
   */
  function handleStreamMessage(messageData) {
    try {
      const update = JSON.parse(messageData);

      if (update.id && update.status !== undefined) {
        // Server is using the 'rxc' format, just prepend 'cell-'
        const cellId = `cell-${update.id}`;
        const cellElement = document.getElementById(cellId);

        if (cellElement) {
          // Get the previous status before removing classes
          const previousStatus = getCellStatus(cellElement);

          // Only update if the status has changed
          if (previousStatus !== update.status) {
            // Remove existing status classes first
            cellElement.classList.remove('cell-red', 'cell-green', 'cell-blue', 'cell-yellow');

            // Update cell counts
            updateCellCounts(previousStatus, update.status);

            // Add the appropriate class based on status
            if (update.status !== 'default') {
              cellElement.classList.add(`cell-${update.status}`);
            }

            // Calculate and display elapsed time if available
            if (update.updatedAt && update.status !== 'default') {
              const updatedAt = new Date(update.updatedAt);
              const viewAt = new Date(update.viewAt);
              const elapsedMs = viewAt - updatedAt;

              if (elapsedMs >= 0 && elapsedMs <= 9999) {
                cellElement.textContent = elapsedMs;
                cellElement.classList.add('has-elapsed-time');
              } else {
                cellElement.textContent = '';
                cellElement.classList.remove('has-elapsed-time');
              }
            } else {
              // Clear text content for default state
              cellElement.textContent = '';
              cellElement.classList.remove('has-elapsed-time');
            }

            // Update the grid summary display
            updateGridSummary();
          }
        }
      }
    } catch (error) {
      console.error('Error parsing stream message:', error, 'Data:', messageData);
    }
  }

  /**
   * Fetches the current list of sensors and processes each one
   * Handles pagination for large sensor lists
   */
  async function fetchSensorList() {
    await fetchSensorPage('start');
  }

  /**
   * Fetches a page of sensors and processes them
   * @param {string} pageToken - The page token for pagination ('start' for first page)
   */
  async function fetchSensorPage(pageToken) {
    try {
      const url = `${origin}/sensor/paginated-list/${pageToken}`;
      // console.log(`Fetching sensor page from ${url}...`);

      const response = await fetch(url);

      if (!response.ok) {
        console.error(`HTTP error! Status: ${response.status}`, await response.text());
        return;
      }

      const data = await response.json();

      if (data && data.sensors && Array.isArray(data.sensors)) {
        // console.log(`Received ${data.sensors.length} sensors from page ${pageToken}`);

        // Process each sensor through the handleStreamMessage function
        data.sensors.forEach((sensor) => {
          // Convert the sensor object to a JSON string as handleStreamMessage expects
          const sensorJson = JSON.stringify(sensor);
          handleStreamMessage(sensorJson);
        });

        // Check if there are more pages to fetch
        if (data.hasMore && data.nextPageToken) {
          // Fetch the next page
          await fetchSensorPage(data.nextPageToken);
        }
      } else {
        console.error('Invalid response format:', data);
      }
    } catch (error) {
      console.error(`Error fetching sensor page ${pageToken}:`, error);
    }
  }

  /**
   * Establishes and manages the Server-Sent Events (SSE) connection.
   */
  function connectToStream() {
    if (eventSource && eventSource.readyState !== EventSource.CLOSED) {
      console.log('EventSource already open or connecting.');
      return;
    }

    console.log(`Attempting to connect SSE to ${viewStreamUrl}...`);
    updateConnectionStatus('Connecting...', '');
    eventSource = new EventSource(viewStreamUrl);

    eventSource.onopen = (event) => {
      // console.log('SSE connection established.');
      updateConnectionStatus('Connected', 'connected');
    };

    eventSource.onmessage = (event) => {
      handleStreamMessage(event.data);
    };

    eventSource.onerror = (event) => {
      if (eventSource.readyState === EventSource.CONNECTING) {
        return;
      }
      console.error('SSE error:', event);
      updateConnectionStatus('Error', 'error');

      if (eventSource.readyState === EventSource.CLOSED) {
        console.log('SSE connection closed.');
        updateConnectionStatus('Disconnected', 'error');
        eventSource = null; // Clear the instance

        // Optional: Attempt to reconnect after a delay
        console.log('Attempting to reconnect in 5 seconds...');
        setTimeout(connectToStream, 5000);
      }
    };
  }

  /**
   * Handles global keydown events for cell updates and selection mode.
   * @param {KeyboardEvent} event
   */
  function handleGlobalKeyDown(event) {
    // Toggle selection mode with Shift key
    if (event.key === 'Shift' && !event.repeat) {
      selectionMode = true;
      document.body.classList.add('selection-active');
      updateSelectionStatus('Selection mode active - Click and drag to select cells');
      return;
    }

    // Handle color keys
    if (['r', 'g', 'b', 'y', 'd'].includes(event.key.toLowerCase())) {
      event.preventDefault(); // Prevent default browser action
      const action = event.key.toLowerCase();

      if (currentSelection.length > 0) {
        // Apply to all selected cells
        currentSelection.forEach((id) => {
          sendCellUpdate(id, action);
        });

        // Clear selection after applying
        clearSelection();
        updateSelectionStatus('Color applied to selection');
      } else if (hoveredCellId) {
        // Extract "R-C" from "cell-R-C"
        const id = hoveredCellId.substring(5); // Remove "cell-" prefix
        sendCellUpdate(id, action);
      }
    }
  }

  /**
   * Handles global keyup events.
   * @param {KeyboardEvent} event
   */
  function handleGlobalKeyUp(event) {
    if (event.key === 'Shift') {
      selectionMode = false;
      document.body.classList.remove('selection-active');
      clearSelection();
      updateSelectionStatus('');
    }
  }

  /**
   * Gets cell coordinates from a cell ID.
   * @param {string} cellId - The cell ID in format "cell-RxC"
   * @returns {Object} - Object with row and col properties
   */
  function getCellCoordinates(cellId) {
    // Make sure the cell ID starts with 'cell-'
    if (!cellId.startsWith('cell-')) {
      console.error('Invalid cell ID format, missing prefix:', cellId);
      return { row: 0, col: 0 };
    }

    // Extract the coordinates part (after 'cell-')
    const coordPart = cellId.substring(5); // Remove 'cell-' prefix
    const xIndex = coordPart.indexOf('x');

    if (xIndex === -1) {
      console.error('Invalid cell ID format, missing x separator:', cellId);
      return { row: 0, col: 0 };
    }

    // Parse the row and column parts
    const row = parseInt(coordPart.substring(0, xIndex));
    const col = parseInt(coordPart.substring(xIndex + 1));

    // Validate the parsed values
    if (isNaN(row) || isNaN(col)) {
      console.error('Invalid cell coordinates:', cellId, row, col);
      return { row: 0, col: 0 };
    }

    return { row, col };
  }

  /**
   * Updates the selection preview.
   */
  function updateSelectionPreview() {
    clearSelectionHighlight();

    if (!selectionStart || !selectionEnd) return;

    // Get the rectangle bounds
    const startRow = Math.min(selectionStart.row, selectionEnd.row);
    const endRow = Math.max(selectionStart.row, selectionEnd.row);
    const startCol = Math.min(selectionStart.col, selectionEnd.col);
    const endCol = Math.max(selectionStart.col, selectionEnd.col);

    // Highlight all cells in the rectangle
    currentSelection = [];
    for (let r = startRow; r <= endRow; r++) {
      for (let c = startCol; c <= endCol; c++) {
        // Use the actual cell coordinates (already include viewport offset)
        const cellId = `cell-${r}x${c}`;
        const cell = document.getElementById(cellId);
        if (cell) {
          highlightCell(cell);
          currentSelection.push(`${r}x${c}`); // Store ID without "cell-" prefix
        }
      }
    }

    updateSelectionStatus(`${currentSelection.length} cells selected`);
  }

  /**
   * Finalizes the selection.
   */
  function finalizeSelection() {
    // Selection is now ready for color application
    updateSelectionStatus(`${currentSelection.length} cells selected. Press r/g/b/y/d to apply color.`);
  }

  /**
   * Highlights a cell as part of the selection.
   * @param {HTMLElement} cell - The cell element to highlight
   */
  function highlightCell(cell) {
    cell.classList.add('selection-highlight');
  }

  /**
   * Clears all selection highlights.
   */
  function clearSelectionHighlight() {
    document.querySelectorAll('.selection-highlight').forEach((cell) => {
      cell.classList.remove('selection-highlight');
    });
  }

  /**
   * Clears the current selection.
   */
  function clearSelection() {
    clearSelectionHighlight();
    currentSelection = [];
    selectionStart = null;
    selectionEnd = null;
  }

  /**
   * Updates the selection status message.
   * @param {string} message - The status message to display
   */
  function updateSelectionStatus(message) {
    // Create status element if it doesn't exist
    let statusElement = document.getElementById('selection-status');
    if (!statusElement) {
      statusElement = document.createElement('div');
      statusElement.id = 'selection-status';
      statusElement.className = 'status-message';
      document.getElementById('info-panel').appendChild(statusElement);
    }

    statusElement.textContent = message;

    // Hide after 3 seconds if empty message
    if (!message) {
      setTimeout(() => {
        statusElement.textContent = '';
      }, 3000);
    }
  }

  /**
   * Parse URL query parameters and set the viewport position
   */
  function parseViewportQueryParams() {
    const urlParams = new URLSearchParams(window.location.search);

    // Check for x parameter
    if (urlParams.has('x')) {
      const xParam = parseInt(urlParams.get('x'));
      if (!isNaN(xParam)) {
        // Round to nearest 10
        viewportX = Math.round(xParam / 10) * 10;
        // Ensure within bounds
        viewportX = Math.max(MIN_GRID_COORD, Math.min(MAX_GRID_COORD, viewportX));
      }
    }

    // Check for y parameter
    if (urlParams.has('y')) {
      const yParam = parseInt(urlParams.get('y'));
      if (!isNaN(yParam)) {
        // Round to nearest 10
        viewportY = Math.round(yParam / 10) * 10;
        // Ensure within bounds
        viewportY = Math.max(MIN_GRID_COORD, Math.min(MAX_GRID_COORD, viewportY));
      }
    }

    // Update the URL display to show the viewport position
    regionUrlSpan.textContent = `${origin} (Grid position: ${viewportX},${viewportY})`;
  }

  // --- Initialization ---
  parseViewportQueryParams(); // Parse query parameters for viewport position
  regionUrlSpan.textContent = `${origin} (Grid position: ${viewportX},${viewportY})`;
  createGrid();
  fetchSensorList(); // Fetch initial state
  // connectToStream(); // Connect to stream for updates
  document.addEventListener('keydown', handleGlobalKeyDown);
  document.addEventListener('keyup', handleGlobalKeyUp);

  // Set up interval to fetch sensor list every 250ms
  const urlParams = new URLSearchParams(window.location.search);
  const interval = parseInt(urlParams.get('interval'), 10) || 250;
  sensorListInterval = setInterval(fetchSensorList, interval);

  // Add window resize event listener to adjust grid when window size changes
  window.addEventListener('resize', () => {
    // Use debounce to avoid excessive recalculations during resize
    clearTimeout(window.resizeTimer);
    window.resizeTimer = setTimeout(() => {
      console.log('Window resized, recalculating grid dimensions...');
      createGrid();
      // Recreate axes after grid is resized
      setTimeout(() => {
        createLeftAxis();
        createBottomAxis();
      }, 0);
    }, 250); // Wait 250ms after resize ends before recalculating
  });
}); // End DOMContentLoaded
