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
  const viewStreamUrl = `${origin}/grid-cell/stream`; // SSE URL
  const viewListUrl = `${origin}/grid-cell/list`; // SSE URL

  // --- State ---
  let hoveredCellId = null; // ID of the currently hovered cell ('cell-R-C')
  let eventSource = null; // EventSource instance
  let gridCellListInterval = null; // Interval timer for fetching grid cell list

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
    orange: 0,
    predator: 0,
  };

  // --- DOM References ---
  const gridPositionElement = document.getElementById('grid-position');
  // Create and insert mouse position display after grid position
  let mousePositionElement = document.getElementById('mouse-position');
  if (!mousePositionElement && gridPositionElement && gridPositionElement.parentNode) {
    mousePositionElement = document.createElement('div');
    mousePositionElement.id = 'mouse-position';
    mousePositionElement.style.marginLeft = '18px';
    mousePositionElement.style.display = 'inline-block';
    mousePositionElement.style.color = 'white';
    mousePositionElement.style.fontSize = '0.9em';
    mousePositionElement.style.fontWeight = 'bold';
    mousePositionElement.style.background = 'rgba(5,10,25,0.4)';
    mousePositionElement.style.padding = '3px 8px';
    mousePositionElement.style.borderRadius = '3px';
    mousePositionElement.style.border = '1px solid rgba(0,100,200,0.2)';
    gridPositionElement.parentNode.insertBefore(mousePositionElement, gridPositionElement);
  }

  function updateMousePositionDisplay(x, y) {
    if (mousePositionElement) {
      mousePositionElement.textContent = `Mouse Position: x=${x}, y=${y}`;
    }
  }

  function clearMousePositionDisplay() {
    if (mousePositionElement) {
      mousePositionElement.textContent = '';
    }
  }

  const gridContainer = document.getElementById('grid-container');
  const leftAxis = document.getElementById('left-axis');
  const bottomAxis = document.getElementById('bottom-axis');
  const regionNameSpan = document.getElementById('region-name');
  const connectionStatusSpan = document.getElementById('connection-status');
  const gridSummary = document.getElementById('grid-summary');

  // --- Functions ---

  // Mouse position tracking on grid
  if (gridContainer) {
    gridContainer.addEventListener('mousemove', (e) => {
      // Get bounding rect of grid
      const rect = gridContainer.getBoundingClientRect();
      // Get mouse position relative to grid
      const px = e.clientX - rect.left;
      const py = e.clientY - rect.top;
      // Calculate cell size (assume uniform)
      const cellWidth = rect.width / gridCols;
      const cellHeight = rect.height / gridRows;
      // Compute grid coordinates
      let gridX = Math.floor(px / cellWidth) + viewportX;
      let gridY = Math.floor(py / cellHeight) + viewportY;
      // Clamp to grid bounds
      gridX = Math.max(MIN_GRID_COORD, Math.min(MAX_GRID_COORD, gridX));
      gridY = Math.max(MIN_GRID_COORD, Math.min(MAX_GRID_COORD, gridY));
      updateMousePositionDisplay(gridX, gridY);
    });
    gridContainer.addEventListener('mouseleave', clearMousePositionDisplay);
  }

  /**
   * Updates the grid summary display with current cell counts
   */
  function updateGridSummary() {
    gridSummary.textContent = `Total: ${cellCounts.total}, R: ${cellCounts.red}, G: ${cellCounts.green}, B: ${cellCounts.blue}, O: ${cellCounts.orange}, P: ${cellCounts.predator}`;
  }

  /**
   * Updates the cell counts when a cell status changes
   * @param {string} oldStatus Previous cell status
   * @param {string} newStatus New cell status
   */
  function updateCellCounts(oldStatus, newStatus) {
    // Decrement the old status count if it was active
    if (oldStatus !== 'inactive') {
      cellCounts[oldStatus]--;
      cellCounts.total--;
    }

    // Increment the new status count if it's active
    if (newStatus !== 'inactive') {
      cellCounts[newStatus]++;
      cellCounts.total++;
    }
  }

  /**
   * Gets the current status of a cell from its classes
   * @param {HTMLElement} cellElement The cell element
   * @returns {string} The cell status ('red', 'green', 'blue', 'orange', 'predator', or 'inactive')
   */
  function getCellStatus(cellElement) {
    if (cellElement.classList.contains('cell-red')) return 'red';
    if (cellElement.classList.contains('cell-green')) return 'green';
    if (cellElement.classList.contains('cell-blue')) return 'blue';
    if (cellElement.classList.contains('cell-orange')) return 'orange';
    if (cellElement.classList.contains('cell-predator')) return 'predator';
    return 'inactive';
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
   * Creates and adds a command status display to the info panel
   */
  function createCommandDisplay() {
    const infoPanel = document.getElementById('info-panel');
    if (!infoPanel) return;

    // Create command status display if it doesn't exist
    if (!document.getElementById('command-status')) {
      const commandStatus = document.createElement('div');
      commandStatus.id = 'command-status';
      commandStatus.textContent = 'Type number + x/y/h/j/k/l to navigate (e.g., 100x, 50h, 30j)';
      infoPanel.appendChild(commandStatus);
    }
  }

  /**
   * Updates the grid position display in the info panel
   */
  function updateGridPositionDisplay() {
    const gridPositionElement = document.getElementById('grid-position');
    if (gridPositionElement) {
      gridPositionElement.textContent = `Grid Position: x=${viewportX}, y=${viewportY}`;
    }
  }

  /**
   * Calculates the optimal number of rows and columns to fill the available space
   * based on the current viewport dimensions and minimum cell size.
   */
  function calculateGridDimensions() {
    // Get the info panel height for top spacing reference
    const infoPanelHeight = document.getElementById('info-panel').offsetHeight;

    // Use a fixed border for all 4 sides
    const sideMargin = infoPanelHeight * 2; // px
    const availableWidth = Math.max(cellMinSize, window.innerWidth - 2 * sideMargin);
    const availableHeight = Math.max(cellMinSize, window.innerHeight - infoPanelHeight - 2 * sideMargin);

    // Calculate number of cells that can fit (accounting for 3px gap between cells)
    gridCols = Math.max(1, Math.floor(availableWidth / (cellMinSize + 3)));
    gridRows = Math.max(1, Math.floor(availableHeight / (cellMinSize + 3)));

    console.info(`${new Date().toISOString()} `, `Calculated grid dimensions: ${gridRows}x${gridCols} based on viewport ${window.innerWidth}x${window.innerHeight}, border ${sideMargin}px`);
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
    // Remove any lingering overlay from previous grid
    removeGridCellOverlay();
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
        const cellId = `${actualRow}x${actualCol}`;
        cell.id = `cell-${cellId}`;

        // Grid cell overlay hover logic
        let hoverTimer = null;
        cell.addEventListener('mouseenter', () => {
          removeGridCellOverlay();
          // Only show overlay for cells with 'has-elapsed-time'
          if (!cell.classList.contains('has-elapsed-time')) {
            return;
          }
          hoverTimer = setTimeout(async () => {
            // Double-check class in case cell state changed during delay
            if (!cell.classList.contains('has-elapsed-time')) return;
            fetchTimingOverlayData();
          }, 1500);
        });
        cell.addEventListener('mouseleave', () => {
          clearTimeout(hoverTimer);
          removeGridCellOverlay();
        });

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
    console.info(`${new Date().toISOString()} `, `Grid created with ${gridRows}x${gridCols} cells.`);

    gridContainer.style.gridTemplateColumns = `repeat(${gridCols}, minmax(${cellMinSize}px, 1fr))`;
    gridContainer.style.gridTemplateRows = `repeat(${gridRows}, minmax(${cellMinSize}px, 1fr))`;

    // Reset cell counts
    cellCounts = {
      total: 0,
      red: 0,
      green: 0,
      blue: 0,
      orange: 0,
      predator: 0,
    };

    // Update the grid summary display
    updateGridSummary();

    // Create axes after grid is populated
    setTimeout(() => {
      createLeftAxis();
      createBottomAxis();
    }, 0);

    closeStream();
    connectToStream();
  }

  /**
   * Sends the update command to the backend via HTTP PUT.
   * @param {string} id The cell's entity ID (e.g., "RxC" format)
   * @param {string} colorChar The action key ('r', 'g', 'b', 'd')
   * @param {string} command The command to send ('update-status', 'span-status', 'fill-status', 'clear-status', 'erase-status')
   * @param {number} radius The radius for span and fill commands
   */
  async function sendCellUpdate(id, colorChar, command, radius) {
    // Use 'RxC' as the service ID format (no conversion needed)
    const serverFormatId = id;
    const apiUrl = `${origin}/grid-cell/${command}`;
    const statusMap = { r: 'red', g: 'green', b: 'blue', o: 'orange', p: 'predator', d: 'inactive' };
    const status = statusMap[colorChar];
    const centerX = parseInt(id.split('x')[1]);
    const centerY = parseInt(id.split('x')[0]);
    const maxRetries = 10;
    const retryDelay = 100; // ms
    let attempt = 0;
    let success = false;
    let lastError = null;

    while (attempt < maxRetries && !success) {
      const clientAt = new Date().toISOString();
      attempt++;
      if (attempt > 1) {
        console.warn(`${new Date().toISOString()} `, `Retrying PUT to ${apiUrl} with id: ${serverFormatId}, status: ${status}, clientAt: ${clientAt}, cx: ${centerX}, cy: ${centerY}, r: ${radius}`);
      } else {
        console.info(`${new Date().toISOString()} `, `Sending PUT to ${apiUrl} with id: ${serverFormatId}, status: ${status}, clientAt: ${clientAt}, cx: ${centerX}, cy: ${centerY}, r: ${radius}`);
      }
      try {
        const response = await fetch(apiUrl, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            id: serverFormatId,
            status: status,
            clientAt: clientAt,
            centerX: centerX,
            centerY: centerY,
            radius: radius,
          }),
        });

        if (response.ok) {
          console.info(`${new Date().toISOString()} `, `Update request for ${id} sent successfully.`);
          success = true;
        } else {
          const errorText = await response.text();
          lastError = `HTTP error for ${id}! Status: ${response.status} ${errorText}`;
          console.error(`${new Date().toISOString()} `, lastError);
          if (attempt < maxRetries) {
            await new Promise((res) => setTimeout(res, retryDelay));
          }
        }
      } catch (error) {
        lastError = error;
        console.error(`${new Date().toISOString()} `, `Error sending cell ${id} update:`, error);
        if (attempt < maxRetries) {
          await new Promise((res) => setTimeout(res, retryDelay));
        }
      }
    }
    if (!success) {
      console.error(`${new Date().toISOString()} `, `Failed to update cell ${id} after ${maxRetries} attempts. Last error:`, lastError);
    }
  }

  async function sendCreatePredator(id, range) {
    const apiUrl = `${origin}/grid-cell/create-predator`;
    const response = await fetch(apiUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        id: id,
        status: 'predator',
        clientAt: new Date().toISOString(),
        centerX: parseInt(id.split('x')[1]),
        centerY: parseInt(id.split('x')[0]),
        radius: range,
      }),
    });
    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`);
    }
  }

  /**
   * Sends a request to fill a rectangle with a specific color/status
   * @param {number} x1 - Starting X coordinate
   * @param {number} y1 - Starting Y coordinate
   * @param {number} x2 - Ending X coordinate
   * @param {number} y2 - Ending Y coordinate
   * @param {string} colorChar - Color character ('r', 'g', 'b', 'o', 'p', 'd')
   * @returns {Promise<boolean>} - True if successful, false otherwise
   */
  async function sendFillRectangle(x1, y1, x2, y2, colorChar) {
    const apiUrl = `${origin}/grid-cell/fill-rectangle`;
    const statusMap = { r: 'red', g: 'green', b: 'blue', o: 'orange', p: 'predator', d: 'inactive' };
    const status = statusMap[colorChar];
    const clientAt = new Date().toISOString();
    const maxRetries = 5;
    const retryDelay = 200; // ms
    let attempt = 0;
    let success = false;
    let lastError = null;

    while (attempt < maxRetries && !success) {
      attempt++;
      if (attempt > 1) {
        console.warn(`${new Date().toISOString()} `, `Retrying PUT to ${apiUrl} for rectangle (${x1},${y1})-(${x2},${y2}), status: ${status}`);
      } else {
        console.info(`${new Date().toISOString()} `, `Sending PUT to ${apiUrl} for rectangle (${x1},${y1})-(${x2},${y2}), status: ${status}`);
      }

      try {
        const response = await fetch(apiUrl, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            x1: x1,
            y1: y1,
            x2: x2,
            y2: y2,
            status: status,
            clientAt: clientAt,
            endpointAt: clientAt, // Server will override this
            region: 'client-side', // Server will override this with actual region
          }),
        });

        if (response.ok) {
          console.info(`${new Date().toISOString()} `, `Fill rectangle request for (${x1},${y1})-(${x2},${y2}) sent successfully.`);
          success = true;
        } else {
          const errorText = await response.text();
          lastError = `HTTP error for rectangle! Status: ${response.status} ${errorText}`;
          console.error(`${new Date().toISOString()} `, lastError);
          if (attempt < maxRetries) {
            await new Promise((res) => setTimeout(res, retryDelay));
          }
        }
      } catch (error) {
        lastError = error;
        console.error(`${new Date().toISOString()} `, `Error sending rectangle fill:`, error);
        if (attempt < maxRetries) {
          await new Promise((res) => setTimeout(res, retryDelay));
        }
      }
    }

    if (!success) {
      console.error(`${new Date().toISOString()} `, `Failed to fill rectangle after ${maxRetries} attempts. Last error:`, lastError);
      return false;
    }

    return true;
  }

  /**
   * Handles incoming messages from the SSE stream or a query response.
   * @param {string} gridCellJson Raw message data string (expected JSON)
   */
  function handleGridCellData(gridCellJson) {
    try {
      const gridCell = JSON.parse(gridCellJson);

      if (gridCell.id && gridCell.status !== undefined) {
        // Server is using the 'rxc' format, just prepend 'cell-'
        const cellId = `cell-${gridCell.id}`;
        const gridCellElement = document.getElementById(cellId);

        if (gridCellElement) {
          // Get the previous status before removing classes
          const previousStatus = getCellStatus(gridCellElement);

          // Only update if the status has changed
          if (previousStatus !== gridCell.status) {
            // Remove existing status classes first
            gridCellElement.classList.remove('cell-red', 'cell-green', 'cell-blue', 'cell-orange', 'cell-predator');

            // Update cell counts
            updateCellCounts(previousStatus, gridCell.status);

            // Add the appropriate class based on status
            if (gridCell.status !== 'inactive') {
              gridCellElement.classList.add(`cell-${gridCell.status}`);
            }

            // Calculate and display elapsed time if available
            if (gridCell.updatedAt && gridCell.status !== 'inactive') {
              const elapsedMs = Math.min(9999, gridCell.elapsedMs);

              if (elapsedMs >= 0) {
                gridCellElement.textContent = elapsedMs;
                gridCellElement.classList.add('has-elapsed-time');
              } else {
                gridCellElement.textContent = '';
                gridCellElement.classList.remove('has-elapsed-time');
              }
            } else {
              // Clear text content for inactive state
              gridCellElement.textContent = '';
              gridCellElement.classList.remove('has-elapsed-time');
            }

            // Update the grid summary display
            updateGridSummary();
          }
        }
      }
    } catch (error) {
      console.error('Error parsing stream message:', error, 'Data:', gridCellJson);
    }
  }

  /**
   * Fetches the current list of grid cells and processes each one
   * Handles pagination for large grid cell lists
   */
  async function fetchGridCellList() {
    // await fetchGridCellData('start');

    const regions = subdivideGrid(viewportY, viewportX, gridRows, gridCols, 500);

    for (const region of regions) {
      await queryGridCellData(region, 'start');
    }
  }

  /**
   * Fetches a page of grid cells and processes them
   * @param {string} pageToken - The page token for pagination ('start' for first page)
   */
  async function fetchGridCellData(pageToken) {
    try {
      const x1 = viewportX; // Current viewport X offset
      const y1 = viewportY; // Current viewport Y offset
      const x2 = x1 + gridCols; // End of viewport X offset
      const y2 = y1 + gridRows; // End of viewport Y offset
      const url = `${origin}/grid-cell/paginated-list/${x1}/${y1}/${x2}/${y2}/${pageToken}`;
      // console.info(`Fetching grid cell data from ${url}...`);

      const response = await fetch(url);

      if (!response.ok) {
        console.error(`HTTP error! Status: ${response.status}`, await response.text());
        return;
      }

      const data = await response.json();

      if (data && data.gridCells && Array.isArray(data.gridCells)) {
        // console.info(`Received ${data.cells.length} cells from page ${pageToken}`);

        // Process each cell through the handleStreamMessage function
        data.gridCells.forEach((cell) => {
          // Convert the grid cell object to a JSON string as handleStreamMessage expects
          const gridCellJson = JSON.stringify(cell);
          handleGridCellData(gridCellJson);
        });

        // Check if there are more pages to fetch
        if (data.hasMore && data.nextPageToken) {
          // Fetch the next page
          await fetchGridCellData(data.nextPageToken);
        }
      } else {
        console.error('Invalid response format:', data);
      }
    } catch (error) {
      console.error(`Error fetching grid cell page ${pageToken}:`, error);
    }
  }

  async function queryGridCellData(region, pageToken) {
    try {
      const x1 = region.topLeft.col + viewportX; // Current viewport X offset
      const y1 = region.topLeft.row + viewportY; // Current viewport Y offset
      const x2 = x1 + region.dimensions.cols; // End of viewport X offset
      const y2 = y1 + region.dimensions.rows; // End of viewport Y offset
      const url = `${origin}/grid-cell/paginated-list/${x1}/${y1}/${x2}/${y2}/${pageToken}`;
      // console.info(`Fetching grid cell data from ${url}...`);

      const response = await fetch(url);

      if (!response.ok) {
        console.error(`HTTP error! Status: ${response.status}`, await response.text());
        return;
      }

      const data = await response.json();

      if (data && data.gridCells && Array.isArray(data.gridCells)) {
        // console.info(`Received ${data.cells.length} cells from page ${pageToken}`);

        // Process each cell through the handleStreamMessage function
        data.gridCells.forEach((cell) => {
          // Convert the grid cell object to a JSON string as handleStreamMessage expects
          const gridCellJson = JSON.stringify(cell);
          handleGridCellData(gridCellJson);
        });

        // Check if there are more pages to fetch
        if (data.hasMore && data.nextPageToken) {
          // Fetch the next page
          await queryGridCellData(region, data.nextPageToken);
        }
      } else {
        console.error('Invalid response format:', data);
      }
    } catch (error) {
      console.error(`Error fetching grid cell page ${pageToken}:`, error);
    }
  }

  function subdivideGrid(topLeftRow, topLeftCol, rows, cols, maxCells) {
    const regions = [];

    function subdivideRegion(r1, c1, r2, c2) {
      const regionRows = r2 - r1 + 1;
      const regionCols = c2 - c1 + 1;
      const cellCount = regionRows * regionCols;

      // If region is within max cells, add it to results
      if (cellCount <= maxCells) {
        regions.push({
          idTopLeft: `${r1}x${c1}`,
          idBottomRight: `${r2}x${c2}`,
          topLeft: { row: r1, col: c1 },
          bottomRight: { row: r2, col: c2 },
          dimensions: { rows: regionRows, cols: regionCols },
          cellCount: cellCount,
        });
        return;
      }

      // Determine split direction - prefer splitting the longer dimension
      const splitVertically = regionRows >= regionCols;

      if (splitVertically) {
        // Split horizontally (divide rows)
        const midRow = Math.floor((r1 + r2) / 2);
        subdivideRegion(r1, c1, midRow, c2);
        subdivideRegion(midRow + 1, c1, r2, c2);
      } else {
        // Split vertically (divide columns)
        const midCol = Math.floor((c1 + c2) / 2);
        subdivideRegion(r1, c1, r2, midCol);
        subdivideRegion(r1, midCol + 1, r2, c2);
      }
    }

    // Start subdivision with the entire grid (0-indexed)
    subdivideRegion(topLeftRow, topLeftCol, topLeftRow + rows - 1, topLeftCol + cols - 1);

    return regions;
  }

  /**
   * Establishes and manages the Server-Sent Events (SSE) connection.
   */
  function closeStream() {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
      updateConnectionStatus('Disconnected', 'error');
      console.info(`${new Date().toISOString()} `, 'EventSource closed.');
    }
  }

  /**
   * Connects to the /grid-cell/current-time event stream and logs messages.
   */
  function connectToTimeStream() {
    const url = `${origin}/grid-cell/current-time`;
    const timeSource = new EventSource(url);
    console.log(`${new Date().toISOString()} `, `Connecting to time stream at ${url}`);

    timeSource.onopen = () => {
      console.log(`${new Date().toISOString()} `, 'Time stream connection established.');
    };

    timeSource.onmessage = (event) => {
      if (event.data) {
        console.log(`${new Date().toISOString()} `, '[Time Stream]', event.data);
      }
    };

    timeSource.onerror = (event) => {
      console.log(`${new Date().toISOString()} `, 'Time stream error:', event);
      if (timeSource.readyState === EventSource.CLOSED) {
        console.log(`${new Date().toISOString()} `, 'Time stream connection closed.');
      }
    };
  }

  function connectToStream() {
    if (eventSource && eventSource.readyState !== EventSource.CLOSED) {
      console.info(`${new Date().toISOString()} `, 'EventSource already open or connecting.');
      return;
    }

    const x1 = viewportX; // Current viewport X offset
    const y1 = viewportY; // Current viewport Y offset
    const x2 = x1 + gridCols; // End of viewport X offset
    const y2 = y1 + gridRows; // End of viewport Y offset
    const url = `${viewStreamUrl}/${x1}/${y1}/${x2}/${y2}`;
    console.info(`${new Date().toISOString()} `, `Attempting to connect SSE to ${url}...`);
    updateConnectionStatus('Connecting...', '');
    eventSource = new EventSource(url);

    const readyStateMap = {
      0: '(0) Connecting',
      1: '(1) Open',
      2: '(2) Closed',
    };

    eventSource.onopen = (event) => {
      console.info(`${new Date().toISOString()} `, `SSE connection established, readyState: ${readyStateMap[eventSource.readyState]}.`);
      updateConnectionStatus('Connected', 'connected');
    };

    eventSource.onmessage = (event) => {
      if (event.data) {
        // console.debug(`${new Date().toISOString()} SSE message: ${event.data}`);
        handleGridCellData(event.data);
      }
    };

    eventSource.onerror = (event) => {
      // console.error(`${new Date().toISOString()} `, `SSE error:`, event);
      console.error(`${new Date().toISOString()} `, `SSE error, EventSource readyState: ${readyStateMap[eventSource.readyState]}`);
      if (eventSource.readyState === EventSource.CONNECTING) {
        return;
      }
      console.error('SSE error:', event);
      updateConnectionStatus('Error', 'error');

      if (eventSource.readyState === EventSource.CLOSED) {
        console.info(`${new Date().toISOString()} `, 'SSE connection closed.');
        updateConnectionStatus('Disconnected', 'error');
        eventSource = null; // Clear the instance

        // Optional: Attempt to reconnect after a delay
        console.info(`${new Date().toISOString()} `, 'Attempting to reconnect in 5 seconds...');
        setTimeout(connectToStream, 5000);
      }
    };
  }

  /**
   * Parses a viewport command in various formats:
   * - '123x' or '-76y': Absolute positioning
   * - '50h', '30j', '20k', '40l': Relative movement (left, down, up, right)
   * @param {string} command - The command string
   * @returns {Object|null} - Object with movement data or null if invalid
   */
  function parseViewportCommand(command) {
    // Check if the command is empty
    if (!command || command.length < 2) {
      return null;
    }

    const lastChar = command.charAt(command.length - 1);
    const validCommands = ['x', 'y', 'h', 'j', 'k', 'l'];

    if (!validCommands.includes(lastChar)) {
      return null;
    }

    // Extract the numeric part
    const numericPart = command.substring(0, command.length - 1);
    let value = parseInt(numericPart);

    if (isNaN(value)) {
      return null;
    }

    // For vim-like commands, we only accept positive numbers
    if (['h', 'j', 'k', 'l'].includes(lastChar) && value < 0) {
      return null;
    }

    // Create the appropriate movement object based on the command
    switch (lastChar) {
      case 'x': // Absolute X position
        return { x: value };
      case 'y': // Absolute Y position
        return { y: value };
      case 'h': // Move left
        return { relativeX: -value };
      case 'j': // Move down
        return { relativeY: value };
      case 'k': // Move up
        return { relativeY: -value };
      case 'l': // Move right
        return { relativeX: value };
      default:
        return null;
    }
  }

  /**
   * Updates the viewport position and refreshes the grid
   * @param {number} x - New absolute X coordinate (optional)
   * @param {number} y - New absolute Y coordinate (optional)
   * @param {number} relativeX - Relative X movement (optional)
   * @param {number} relativeY - Relative Y movement (optional)
   */
  function updateViewport(x, y, relativeX, relativeY) {
    let changed = false;
    let newX = viewportX;
    let newY = viewportY;

    // Handle absolute X positioning
    if (x !== undefined) {
      newX = Math.round(x / 10) * 10; // Round to nearest 10
    }

    // Handle absolute Y positioning
    if (y !== undefined) {
      newY = Math.round(y / 10) * 10; // Round to nearest 10
    }

    // Handle relative X movement (h/l commands)
    if (relativeX !== undefined) {
      newX = viewportX + Math.round(relativeX / 10) * 10; // Round to nearest 10
    }

    // Handle relative Y movement (j/k commands)
    if (relativeY !== undefined) {
      newY = viewportY + Math.round(relativeY / 10) * 10; // Round to nearest 10
    }

    // Clamp values to grid boundaries
    const clampedX = Math.max(MIN_GRID_COORD, Math.min(MAX_GRID_COORD, newX));
    const clampedY = Math.max(MIN_GRID_COORD, Math.min(MAX_GRID_COORD, newY));

    // Check if position actually changed
    if (viewportX !== clampedX) {
      viewportX = clampedX;
      changed = true;
    }

    if (viewportY !== clampedY) {
      viewportY = clampedY;
      changed = true;
    }

    if (changed) {
      // Update the grid position display
      updateGridPositionDisplay();

      // Refresh the grid
      createGrid();

      // Show a notification
      updateCommandStatus(`Viewport moved to x:${viewportX}, y:${viewportY}`, 2000);

      // Close and reconnect SSE stream to match new viewport
      closeStream();
      connectToStream();
    }
  }

  // Command buffer for vim-like navigation
  let commandBuffer = '';
  let commandTimeout = null;

  /**
   * Updates the command status display
   * @param {string} message - The message to display
   * @param {number} timeout - Optional timeout in ms to clear the message
   */
  function updateCommandStatus(message, timeout = 0) {
    const commandStatus = document.getElementById('command-status');
    if (commandStatus) {
      commandStatus.textContent = message;

      if (timeout > 0) {
        setTimeout(() => {
          commandStatus.textContent = 'Type number + x/y/h/j/k/l to navigate (e.g., 100x, 50h, 30j)';
        }, timeout);
      }
    }
  }

  /**
   * Handles global keydown events for cell updates, selection mode, and vim-like navigation.
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

    // Handle navigation commands (numbers, minus sign, x, y, h, j, k, l)
    if (/^[0-9\-xyhjkl]$/.test(event.key)) {
      // Clear the command timeout if it exists
      if (commandTimeout) {
        clearTimeout(commandTimeout);
      }

      // Add the key to the command buffer
      commandBuffer += event.key;

      // Update the command status display
      updateCommandStatus(`Command: ${commandBuffer}`);

      // Check if the command is complete (ends with a valid command character)
      if (/[xyhjkl]$/.test(commandBuffer)) {
        const parsedCommand = parseViewportCommand(commandBuffer);

        if (parsedCommand) {
          updateViewport(parsedCommand.x, parsedCommand.y, parsedCommand.relativeX, parsedCommand.relativeY);
          updateCommandStatus(`Executed: ${commandBuffer}`, 2000);
        } else {
          updateCommandStatus(`Invalid command: ${commandBuffer}`, 2000);
        }

        // Reset the command buffer
        commandBuffer = '';
      } else {
        // Set a timeout to clear the command buffer if no key is pressed for 3 seconds
        commandTimeout = setTimeout(() => {
          commandBuffer = '';
          updateCommandStatus('Command timeout. Type number + x/y to navigate', 2000);
        }, 3000);
      }

      return;
    }

    if (commandBuffer.length > 0 && event.key === 'Escape') {
      // Clear the command buffer if Escape is pressed
      commandBuffer = '';
      updateCommandStatus('Command canceled', 2000);
      return;
    }

    // Handle color keys
    if (['r', 'g', 'b', 'o', 'd'].includes(event.key.toLowerCase())) {
      event.preventDefault(); // Prevent default browser action
      const colorChar = event.key.toLowerCase();
      const radius = commandBuffer.length == 0 ? 0 : parseInt(commandBuffer);
      const cellElement = document.getElementById(hoveredCellId);
      const hasElapsedTime = cellElement.classList.contains('has-elapsed-time');
      const command =
        radius == 0 //
          ? 'update-status' //
          : hasElapsedTime //
          ? 'span-status' //
          : 'fill-status'; //

      if (currentSelection.length > 0) {
        const topLeftXy = currentSelection[0].split('x'); // RxC, YxX
        const topLeftX = parseInt(topLeftXy[1]);
        const topLeftY = parseInt(topLeftXy[0]);
        const bottomRightXy = currentSelection[currentSelection.length - 1].split('x');
        const bottomRightX = parseInt(bottomRightXy[1]);
        const bottomRightY = parseInt(bottomRightXy[0]);
        sendFillRectangle(topLeftX, topLeftY, bottomRightX, bottomRightY, colorChar);
        // Apply to all selected cells
        // currentSelection.forEach((id) => {
        //   sendCellUpdate(id, colorChar, command, radius);
        // });

        // Clear selection after applying
        clearSelection();
        updateSelectionStatus('Color applied to selection');
      } else if (hoveredCellId) {
        // Extract "RxC" from "cell-RxC"
        const id = hoveredCellId.substring(5); // Remove "cell-" prefix
        sendCellUpdate(id, colorChar, command, radius);
      }
    }

    // Handle clear command
    if (event.key === 'c') {
      event.preventDefault(); // Prevent default browser action
      const cellElement = document.getElementById(hoveredCellId);
      const hasElapsedTime = cellElement.classList.contains('has-elapsed-time');

      if (hasElapsedTime) {
        const command = 'clear-status';
        const id = hoveredCellId.substring(5); // Remove "cell-" prefix
        const colorChar = cellElement.classList.contains('cell-red')
          ? 'r'
          : cellElement.classList.contains('cell-green')
          ? 'g'
          : cellElement.classList.contains('cell-blue')
          ? 'b'
          : cellElement.classList.contains('cell-orange')
          ? 'o'
          : cellElement.classList.contains('cell-predator')
          ? 'p'
          : '';
        const radius = 0;
        if (colorChar.length > 0) {
          sendCellUpdate(id, colorChar, command, radius);
        }
      }
    }

    // Handle erase command
    if (event.key === 'e') {
      event.preventDefault(); // Prevent default browser action
      const cellElement = document.getElementById(hoveredCellId);
      const hasElapsedTime = cellElement.classList.contains('has-elapsed-time');

      if (hasElapsedTime) {
        const command = 'erase-status';
        const id = hoveredCellId.substring(5); // Remove "cell-" prefix
        const radius = 0;
        sendCellUpdate(id, '', command, radius);
      }
    }

    // Handle cell data details command
    if (event.key === 'q') {
      event.preventDefault(); // Prevent default browser action
      if (hoveredCellId) {
        const cellElement = document.getElementById(hoveredCellId);
        if (cellElement && cellElement.classList.contains('has-elapsed-time')) {
          fetchGridCellOverlayData(cellElement);
        }
      }
    }

    // Handle timings command
    if (event.key === 't') {
      event.preventDefault(); // Prevent default browser action
      const cellElement = document.getElementById(hoveredCellId);
      const hasElapsedTime = cellElement.classList.contains('has-elapsed-time');

      if (hasElapsedTime) {
        fetchTimingOverlayData();
      }
    }

    // Handle predator update command
    if (event.key === 'p') {
      event.preventDefault(); // Prevent default browser action
      const cellElement = document.getElementById(hoveredCellId);

      const id = hoveredCellId.substring(5); // Remove "cell-" prefix
      const range = commandBuffer.length == 0 ? 0 : parseInt(commandBuffer);

      sendCreatePredator(id, range);
    }
  }

  /**
   * Fetches and shows grid cell data for the hovered cell.
   * @param {HTMLElement} cellElement - The cell element to show overlay for
   */
  function fetchGridCellOverlayData(cellElement) {
    // Fetch and show grid cell data for the hovered cell
    const id = hoveredCellId.substring(5); // Remove "cell-" prefix
    fetch(`${origin}/grid-cell/view-row-by-id/${id}`)
      .then((resp) => (resp.ok ? resp.json() : Promise.reject('Failed to fetch grid cell data')))
      .then((data) => {
        showGridCellOverlay(cellElement, data);
      })
      .catch((error) => {
        console.error(`${new Date().toISOString()} `, `Error fetching grid cell data: ${error}`);
      });
  }

  /**
   * Shows a grid overlay with grid cell data on the given cell.
   * @param {HTMLElement} cell
   * @param {Object} data
   */
  function showGridCellOverlay(cell, data) {
    removeGridCellOverlay();
    const overlay = document.createElement('div');
    overlay.className = 'grid-cell-overlay';
    overlay.style.position = 'fixed';
    overlay.style.background = 'rgba(10,20,40,0.98)';
    overlay.style.color = '#a7ecff';
    overlay.style.zIndex = '10000';
    overlay.style.display = 'flex';
    overlay.style.flexDirection = 'column';
    overlay.style.justifyContent = 'center';
    overlay.style.alignItems = 'center';
    overlay.style.fontSize = '0.75em';
    overlay.style.border = '2px solid #be43a4';
    overlay.style.borderRadius = '7px';
    overlay.style.boxShadow = '0 0 16px #be43a4';
    overlay.style.padding = '14px 18px';
    overlay.style.pointerEvents = 'none';
    overlay.style.maxWidth = '350px';
    overlay.style.maxHeight = '70vh';
    overlay.style.overflowY = 'auto';

    // Format data as a table
    const table = document.createElement('table');
    table.style.borderCollapse = 'collapse';
    Object.entries(data).forEach(([key, value]) => {
      const row = document.createElement('tr');
      const k = document.createElement('td');
      k.textContent = key;
      k.style.padding = '2px 6px';
      k.style.fontWeight = 'bold';
      k.style.textAlign = 'right';
      k.style.color = '#6fffc8';
      const v = document.createElement('td');
      v.textContent = key == 'elapsedMs' ? `${value} ms (viewAt - updatedAt)` : value;
      v.style.padding = '2px 6px';
      v.style.textAlign = 'left';
      v.style.color = '#fff';
      row.appendChild(k);
      row.appendChild(v);
      table.appendChild(row);
    });
    overlay.appendChild(table);
    document.body.appendChild(overlay);

    // Position overlay near the cell, but within viewport
    const cellRect = cell.getBoundingClientRect();
    const overlayRect = overlay.getBoundingClientRect();
    let left = cellRect.right + 12;
    let top = cellRect.top;
    // If overlay would go off right edge, move to left side
    if (left + overlayRect.width > window.innerWidth - 8) {
      left = cellRect.left - overlayRect.width - 12;
    }
    // If overlay would go off left edge, clamp to 8px
    if (left < 8) left = 8;
    // If overlay would go off bottom, clamp
    if (top + overlayRect.height > window.innerHeight - 8) {
      top = window.innerHeight - overlayRect.height - 8;
    }
    // If overlay would go off top, clamp
    if (top < 8) top = 8;
    overlay.style.left = `${left}px`;
    overlay.style.top = `${top}px`;
    cell.classList.add('grid-cell-overlay-active');
  }

  /**
   * Removes any grid cell overlay from the grid.
   */
  function removeGridCellOverlay() {
    document.querySelectorAll('.grid-cell-overlay').forEach((el) => {
      if (el.parentNode) {
        if (el.parentNode.classList) {
          el.parentNode.classList.remove('grid-cell-overlay-active');
        }
        el.remove();
      }
    });
  }

  /**
   * Fetches and shows timing data for the hovered cell.
   * @param {HTMLElement} cellElement - The cell element to show overlay for
   */
  function fetchTimingOverlayData() {
    const id = hoveredCellId.substring(5); // Remove "cell-" prefix
    const cellElement = document.getElementById(hoveredCellId);

    getRoutes()
      .then((routes) => {
        console.info(`${new Date().toISOString()} `, `Multi-region routes ${routes}`);
        const dataList = [];
        let completed = 0;

        routes.forEach((route, idx) => {
          let routeUrl;
          if (route.startsWith('localhost') || route.startsWith('127.0.0.1')) {
            routeUrl = `http://${route}/grid-cell/view-row-by-id/${id}`;
          } else {
            routeUrl = `https://${route}/grid-cell/view-row-by-id/${id}`;
          }
          console.info(`${new Date().toISOString()} `, `Timings for region ${routeUrl}`);
          fetch(routeUrl)
            .then((resp) => resp.json())
            .then((data) => {
              dataList[idx] = data;
            })
            .catch((error) => {
              console.warn(`${new Date().toISOString()} `, `Error fetching route data: ${error}`);
              dataList[idx] = null;
            })
            .finally(() => {
              completed++;
              if (completed === routes.length) {
                showTimingOverlay(dataList, cellElement);
              }
            });
        });
      })
      .catch((error) => {
        console.warn(`${new Date().toISOString()} `, `Error fetching routes: ${error}`);
      });
  }

  /**
   * Gets routes either from URL query parameter or by fetching from the server
   * @returns {Promise<Array>} Promise that resolves to an array of routes
   */
  function getRoutes() {
    return new Promise((resolve, reject) => {
      // First check for a "routes" query parameter
      const urlParams = new URLSearchParams(window.location.search);
      const routesParam = urlParams.get('routes');

      if (routesParam) {
        // If routes parameter exists, parse it (assuming comma-separated list)
        const routes = routesParam.split(',');
        console.info(`${new Date().toISOString()} `, `Using routes from URL parameter: ${routes}`);
        resolve(routes);
      } else {
        // Otherwise fetch routes from the server
        fetch('/grid-cell/routes')
          .then((resp) => resp.json())
          .then((routes) => {
            console.info(`${new Date().toISOString()} `, `Fetched multi-region routes: ${routes}`);
            resolve(routes);
          })
          .catch((error) => {
            console.warn(`${new Date().toISOString()} `, `Error fetching routes: ${error}`);
            reject(error);
          });
      }
    });
  }

  /**
   * Displays the timing overlay for a cell.
   * @param {Array} dataList - List of timing data objects from all routes
   * @param {HTMLElement} cellElement - The cell element to show overlay for
   */
  function showTimingOverlay(dataList, cellElement) {
    removeGridCellOverlay();
    // Defensive: filter nulls, parse dates, sort by viewAt ascending
    const validData = dataList.filter((d) => d && d.viewAt && d.endpointAt && d.updatedAt);
    if (!validData.length) return;

    // Parse all relevant dates
    const parsed = validData.map((d) => {
      const obj = { ...d };
      for (const k in obj) {
        if (k.endsWith('At') && obj[k]) obj[k] = new Date(obj[k]);
      }
      return obj;
    });
    parsed.sort((a, b) => a.viewAt - b.viewAt); // oldest to youngest

    const endpointAt0 = parsed[0].endpointAt; // all endpointAt should match
    const updatedAt = parsed[0].updatedAt; // all updatedAt should match
    const youngestViewAt = parsed[parsed.length - 1].viewAt;
    const oldestViewAt = parsed[0].viewAt;
    const gap1 = updatedAt - endpointAt0;
    const gap2 = youngestViewAt - updatedAt;
    // Compensate for excess endpoint to entity elapsed time
    const endpointAt = gap1 > gap2 ? new Date(updatedAt - gap2) : endpointAt0;
    const msRange = youngestViewAt - endpointAt;
    const pxWidth = 400;
    const pxIndent = 10;
    const pxHeight = Math.max(40, parsed.length * 40);
    const overlay = document.createElement('div');
    overlay.className = 'grid-cell-overlay';
    overlay.style.position = 'fixed';
    overlay.style.background = 'rgba(10,20,40,0.98)';
    overlay.style.color = '#a7ecff';
    overlay.style.zIndex = '10000';
    overlay.style.display = 'flex';
    overlay.style.flexDirection = 'column';
    overlay.style.justifyContent = 'center';
    overlay.style.alignItems = 'center';
    overlay.style.fontSize = '0.75em';
    overlay.style.padding = '16px 24px';
    overlay.style.border = '2px solid #be43a4';
    overlay.style.borderRadius = '7px';
    overlay.style.boxShadow = '0 0 16px #be43a4';
    overlay.style.minWidth = pxWidth + 40 + 'px';
    overlay.style.minHeight = pxHeight + 20 + 'px';
    overlay.style.maxWidth = '90vw';
    overlay.style.maxHeight = '80vh';

    // function to create a key table cell
    const createKeyValueRow = (idx, key, value, region) => {
      const row = document.createElement('tr');
      const i = document.createElement('td');
      i.textContent = idx;
      i.style.padding = '2px 6px';
      i.style.textAlign = 'center';
      i.style.color = '#ffffff';
      const k = document.createElement('td');
      k.textContent = key;
      k.style.padding = '2px 6px';
      k.style.fontWeight = 'bold';
      k.style.textAlign = 'right';
      k.style.color = '#6fffc8';
      const v = document.createElement('td');
      v.textContent = value;
      v.style.padding = '2px 6px';
      v.style.textAlign = 'left';
      v.style.color = '#ffffff';
      const r = document.createElement('td');
      r.textContent = region;
      r.style.padding = '2px 6px';
      r.style.textAlign = 'left';
      r.style.color = '#e7bf50';
      row.appendChild(k);
      row.appendChild(v);
      row.appendChild(r);
      row.appendChild(i);
      return row;
    };

    const table = document.createElement('table');
    table.style.marginBottom = '10px';
    table.style.borderCollapse = 'collapse';
    const p = parsed[0];
    table.appendChild(createKeyValueRow('', 'ID', p.id, p.updated));
    table.appendChild(createKeyValueRow('', 'Endpoint to entity', `${p.updatedAt - p.endpointAt} ms`, p.updated));
    table.appendChild(createKeyValueRow('1', 'Entity to view', `${p.viewAt - p.updatedAt} ms`, p.view));
    for (let i = 1; i < parsed.length; i++) {
      const p = parsed[i];
      table.appendChild(createKeyValueRow(`${i + 1}`, 'Entity to view', `${p.viewAt - p.updatedAt} ms`, p.view));
    }
    overlay.appendChild(table);

    // Timings graph SVG setup
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('width', pxWidth);
    svg.setAttribute('height', pxHeight);
    svg.style.display = 'block';
    svg.style.background = 'rgba(20,30,60,0.9)';
    svg.style.borderRadius = '6px';
    svg.style.marginBottom = '8px';

    // Y spacing
    const yStep = pxHeight / (parsed.length + 1);

    // Helper: ms to px (with indent)
    const msToX = (ms) => {
      if (msRange === 0) return pxIndent;
      return Math.round(((ms - endpointAt) / msRange) * (pxWidth - 2 * pxIndent)) + pxIndent;
    };

    // First line: endpointAt -> updatedAt -> oldestViewAt
    const y0 = yStep;
    const xEndpoint = msToX(endpointAt);
    const xUpdated = msToX(updatedAt);
    const xOldestView = msToX(oldestViewAt);
    {
      // Yellow line when length adjusted to compensate for excess endpoint to entity time
      const color = endpointAt0 == endpointAt ? '#a7ecff' : '#f8f53f';
      svg.appendChild(svgLine(xEndpoint, y0, xUpdated, y0, color));
    }
    svg.appendChild(svgLine(xUpdated, y0, xOldestView, y0, '#a7ecff'));

    // Markers for the three points
    const markerColors = ['#44ddff', '#ff4d6f', '#ffd24d'];
    [xEndpoint, xUpdated, xOldestView].forEach((x, i) => {
      svg.appendChild(svgCircle(x, y0, 5, markerColors[i], '#222', '1'));
    });

    // Place "1" above the oldest view circle
    svg.appendChild(svgText(xOldestView, y0 - 12, '1', '15', 'bold', '#ffffff'));

    // Straight lines for each additional region
    for (let i = 1; i < parsed.length; i++) {
      const y = yStep * (i + 1);
      const xStart = xUpdated;
      const xEnd = msToX(parsed[i].viewAt);
      // Draw a straight line from xUpdated (main line) to this region's viewAt at y
      svg.appendChild(svgLine(xStart, y, xEnd, y, '#44ddff'));
      // Draw a vertical line from xUpdated to prior line start
      svg.appendChild(svgLine(xStart, y - yStep + 7, xStart, y, '#44ddff'));
      // Marker at start
      svg.appendChild(svgCircle(xStart, y, 5, '#ff4d6f', '#222', '1'));
      // Marker at end
      svg.appendChild(svgCircle(xEnd, y, 5, '#ffd24d', '#222', '1'));
      // Add centered text with the loop index 'i'
      svg.appendChild(svgText(xEnd, y - 12, i + 1, '15', 'bold', '#ffffff'));
    }

    overlay.appendChild(svg);

    // Position overlay near cell
    document.body.appendChild(overlay);
    const cellRect = cellElement.getBoundingClientRect();
    overlay.style.left = cellRect.right + 12 + 'px';
    overlay.style.top = cellRect.top - 8 + 'px';
    // Clamp if offscreen
    const overlayRect = overlay.getBoundingClientRect();
    let left = overlayRect.left,
      top = overlayRect.top;
    if (left + overlayRect.width > window.innerWidth - 8) {
      left = cellRect.left - overlayRect.width - 12;
    }
    if (left < 8) left = 8;
    if (top + overlayRect.height > window.innerHeight - 8) {
      top = window.innerHeight - overlayRect.height - 8;
    }
    if (top < 8) top = 8;
    overlay.style.left = left + 'px';
    overlay.style.top = top + 'px';
    cellElement.classList.add('grid-cell-overlay-active');

    // Dismiss overlay on click outside or Escape
    function onDismiss(e) {
      if (e.type === 'keydown' && e.key !== 'Escape') return;
      if (e.type === 'mousedown' && !overlay.contains(e.target)) {
        removeGridCellOverlay();
        document.removeEventListener('mousedown', onDismiss, true);
        document.removeEventListener('keydown', onDismiss, true);
      }
      if (e.type === 'keydown' && e.key === 'Escape') {
        removeGridCellOverlay();
        document.removeEventListener('mousedown', onDismiss, true);
        document.removeEventListener('keydown', onDismiss, true);
      }
    }
    setTimeout(() => {
      document.addEventListener('mousedown', onDismiss, true);
      document.addEventListener('keydown', onDismiss, true);
    }, 0);
  }

  function svgLine(x1, y1, x2, y2, color) {
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    line.setAttribute('x1', x1);
    line.setAttribute('y1', y1);
    line.setAttribute('x2', x2);
    line.setAttribute('y2', y2);
    line.setAttribute('stroke', color);
    line.setAttribute('stroke-width', '2');
    return line;
  }

  function svgCircle(x, y, r, fill, stroke, strokeWidth) {
    const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    circle.setAttribute('cx', x);
    circle.setAttribute('cy', y);
    circle.setAttribute('r', r);
    circle.setAttribute('fill', fill);
    circle.setAttribute('stroke', stroke);
    circle.setAttribute('stroke-width', strokeWidth);
    return circle;
  }

  function svgText(x, y, text, fontSize, fontWeight, fill) {
    const textElement = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    textElement.setAttribute('x', x);
    textElement.setAttribute('y', y);
    textElement.setAttribute('text-anchor', 'middle');
    textElement.setAttribute('dominant-baseline', 'middle');
    textElement.setAttribute('font-size', fontSize);
    textElement.setAttribute('font-family', 'Arial, sans-serif');
    textElement.setAttribute('font-weight', fontWeight);
    textElement.setAttribute('fill', fill);
    textElement.textContent = text;
    return textElement;
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
   * Initialize the viewport position to default values
   */
  function initializeViewport() {
    // Set default viewport position (0,0)
    viewportX = 0;
    viewportY = 0;

    // Update the URL display to show the viewport position
    // Fetch region name from backend and display
    fetch(`${origin}/grid-cell/region`)
      .then((resp) => (resp.ok ? resp.text() : Promise.reject('local-development')))
      .then((regionName) => {
        regionNameSpan.textContent = regionName.trim();
      })
      .catch((error) => {
        regionNameSpan.textContent = `${error}`;
      });
  }

  // Fetch and display project version
  // Run bash script version-to-static.sh to update version.txt
  fetch('version.txt')
    .then((resp) => (resp.ok ? resp.text() : Promise.reject('Version not found')))
    .then((version) => {
      document.getElementById('project-version').textContent = `Version: ${version.trim()}`;
    })
    .catch((error) => {
      document.getElementById('project-version').textContent = `Error: ${error}`;
    });

  // --- Initialization ---
  initializeViewport(); // Set default viewport position
  createCommandDisplay(); // Add command status display to the info panel
  updateGridPositionDisplay(); // Update grid position display
  createGrid();
  fetchGridCellList(); // Fetch initial state
  connectToStream(); // Connect to stream for updates
  // connectToTimeStream(); // Connect to time stream for updates
  document.addEventListener('keydown', handleGlobalKeyDown);
  document.addEventListener('keyup', handleGlobalKeyUp);

  // Set up interval to fetch grid cell list every 250ms
  const urlParams = new URLSearchParams(window.location.search);
  const interval = parseInt(urlParams.get('interval'), 10) || 100;
  gridCellListInterval = setInterval(fetchGridCellList, interval);

  // Add window resize event listener to adjust grid when window size changes
  window.addEventListener('resize', () => {
    // Use debounce to avoid excessive recalculations during resize
    clearTimeout(window.resizeTimer);
    window.resizeTimer = setTimeout(() => {
      console.info(`${new Date().toISOString()} `, 'Window resized, recalculating grid dimensions...');
      createGrid();
      // Recreate axes after grid is resized
      setTimeout(() => {
        createLeftAxis();
        createBottomAxis();
      }, 0);
    }, 250); // Wait 250ms after resize ends before recalculating
  });
}); // End DOMContentLoaded
