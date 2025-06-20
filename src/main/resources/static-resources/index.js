document.addEventListener('DOMContentLoaded', async () => {
  // --- Configuration ---
  const config = {
    session: {
      idLength: 5,
      idChars: '0123456789abcdefghijklmnopqrstuvwxyz',
    },

    grid: {
      cellMinSize: 30,
      cellGap: 3,
      minCoord: -1000000,
      maxCoord: 1000000,
      maxCellsPerRegion: 500,
      positionSnap: 10,
    },

    timing: {
      defaultPollingInterval: 100,
      overlayHoverDelay: 1500,
      commandTimeout: 3000,
      windowResizeDebounce: 250,
      selectionClearTimeout: 3000,
      reconnectDelay: 5000,
    },

    retry: {
      maxAttempts: 10,
      delay: 100,
    },

    overlay: {
      maxWidth: 350,
      maxHeightVh: 70,
      timingWidth: 400,
      timingIndent: 10,
      timingMinHeight: 40,
      timingRowHeight: 40,
    },

    agent: {
      messageQueueMax: 100,
    },

    ui: {
      axisTickIntervalMajor: 10,
      axisTickIntervalMinor: 5,
      maxElapsedTimeDisplay: 9999,
      mousePositionStyles: {
        marginLeft: '18px',
        display: 'inline-block',
        color: 'white',
        fontSize: '0.9em',
        fontWeight: 'bold',
        background: 'rgba(5,10,25,0.4)',
        padding: '3px 8px',
        borderRadius: '3px',
        border: '1px solid rgba(0,100,200,0.2)',
      },
    },

    colors: {
      statusMap: {
        r: 'red',
        g: 'green',
        b: 'blue',
        o: 'orange',
        p: 'predator',
        d: 'inactive',
      },
    },

    endpoints: {
      gridCellStream: '/grid-cell/stream',
      gridCellList: '/grid-cell/list',
      gridCellPaginatedList: '/grid-cell/paginated-list',
      gridCellViewById: '/grid-cell/view-row-by-id',
      gridCellRegion: '/grid-cell/region',
      gridCellMultiRegionRoutes: '/grid-cell/multi-region-routes',
      createShape: '/grid-cell/create-shape',
      clearCells: '/grid-cell/clear-cells',
      eraseCells: '/grid-cell/erase-cells',
      createPredator: '/grid-cell/create-predator',
      agentVoiceCommand: '/agent/voice-command',
      agentStepsStream: '/agent/agent-steps-stream',
      agentStepConsumed: '/agent/agent-step-consumed',
    },
  };

  // User's session ID
  function getSessionId() {
    let sessionId = sessionStorage.getItem('sessionId');
    if (!sessionId) {
      // Generate a random ID
      sessionId = Array(config.session.idLength)
        .fill(0)
        .map(() => {
          return config.session.idChars.charAt(Math.floor(Math.random() * config.session.idChars.length));
        })
        .join('');
      sessionStorage.setItem('sessionId', sessionId);
    }
    return sessionId;
  }

  // Grid dimensions will be calculated dynamically based on viewport size
  let gridRows = 0; // Will be calculated dynamically
  let gridCols = 0; // Will be calculated dynamically

  // Recording state
  let isRecording = false; // Track recording state

  // Viewport configuration
  let viewportX = 0; // Current X offset of the viewport (horizontal)
  let viewportY = 0; // Current Y offset of the viewport (vertical)

  // Use the current origin for API calls and SSE stream
  const origin = window.location.origin; // Gets the protocol, hostname, and port
  const viewStreamUrl = `${origin}${config.endpoints.gridCellStream}`;
  const viewListUrl = `${origin}${config.endpoints.gridCellList}`;

  // --- Utility Functions ---

  /**
   * Clamps coordinates to grid boundaries
   */
  function clampToGridBounds(x, y) {
    return {
      x: Math.max(config.grid.minCoord, Math.min(config.grid.maxCoord, x)),
      y: Math.max(config.grid.minCoord, Math.min(config.grid.maxCoord, y)),
    };
  }

  /**
   * Creates a cell ID with the standard format
   */
  function createCellId(row, col) {
    return `cell-${row}x${col}`;
  }

  /**
   * Extracts the coordinate part from a cell element ID
   */
  function extractCellId(cellElementId) {
    return cellElementId.startsWith('cell-') ? cellElementId.substring(5) : cellElementId;
  }

  /**
   * Parses cell coordinates from a cell ID
   */
  function parseCellId(cellId) {
    const cleanId = extractCellId(cellId);
    const parts = cleanId.split('x');
    if (parts.length !== 2) return null;
    const row = parseInt(parts[0]);
    const col = parseInt(parts[1]);
    return isNaN(row) || isNaN(col) ? null : { row, col };
  }

  /**
   * Parses coordinates from ID string (RxC format)
   */
  function parseCoordinatesFromId(id) {
    const parts = id.split('x');
    return {
      x: parseInt(parts[1]),
      y: parseInt(parts[0]),
    };
  }

  // Unified cell status configuration
  const cellStatusConfig = {
    red: { className: 'cell-red', char: 'r', color: 'red' },
    green: { className: 'cell-green', char: 'g', color: 'green' },
    blue: { className: 'cell-blue', char: 'b', color: 'blue' },
    orange: { className: 'cell-orange', char: 'o', color: 'orange' },
    predator: { className: 'cell-predator', char: 'p', color: 'predator' },
  };

  /**
   * Gets the cell status class name from element
   */
  function getCellStatusClass(cellElement) {
    if (!cellElement) return null;
    const statusClasses = Object.values(cellStatusConfig).map((config) => config.className);
    return statusClasses.find((cls) => cellElement.classList.contains(cls)) || null;
  }

  /**
   * Gets the cell status string from element classes
   */
  function getCellStatusFromElement(cellElement) {
    const statusClass = getCellStatusClass(cellElement);
    if (!statusClass) return 'inactive';

    const status = Object.keys(cellStatusConfig).find((key) => cellStatusConfig[key].className === statusClass);
    return status || 'inactive';
  }

  /**
   * Gets the color character from cell element
   */
  function getCellColorCharFromElement(cellElement) {
    const statusClass = getCellStatusClass(cellElement);
    if (!statusClass) return '';

    const status = Object.keys(cellStatusConfig).find((key) => cellStatusConfig[key].className === statusClass);
    return status ? cellStatusConfig[status].char : '';
  }

  /**
   * Creates a base overlay element with standard styling
   */
  // Note: This function was replaced by the unified createOverlay function

  /**
   * Handles fetch errors with consistent logging
   */
  function handleFetchError(error, context, timestamp = true) {
    const logPrefix = timestamp ? formatTimestamp() : '';
    console.error(logPrefix, `Error ${context}:`, error);
  }

  /**
   * Logs info messages with consistent formatting
   */
  function logInfo(message, timestamp = true) {
    const logPrefix = timestamp ? formatTimestamp() : '';
    console.info(logPrefix, message);
  }

  /**
   * Checks if cell has elapsed time data
   */
  function cellHasElapsedTime(cellElement) {
    return cellElement && cellElement.classList.contains('has-elapsed-time');
  }

  /**
   * Updates cell status class
   */
  function updateCellStatus(cellElement, gridCell) {
    if (gridCell.status !== 'inactive') {
      cellElement.classList.add(`cell-${gridCell.status}`);
    }
  }

  /**
   * Updates cell elapsed time display
   */
  function updateCellElapsedTime(cellElement, gridCell) {
    const hasValidElapsedTime = gridCell.updatedAt && gridCell.status !== 'inactive';

    if (hasValidElapsedTime) {
      const elapsedMs = Math.min(config.ui.maxElapsedTimeDisplay, gridCell.elapsedMs);

      if (elapsedMs >= 0) {
        cellElement.textContent = elapsedMs;
        cellElement.classList.add('has-elapsed-time');
        return;
      }
    }

    // Clear text content and elapsed time class
    cellElement.textContent = '';
    cellElement.classList.remove('has-elapsed-time');
  }

  /**
   * Clamps overlay position to stay within viewport
   */
  function clampOverlayPosition(left, top, overlayRect, cellRect) {
    const margin = 8;
    const offset = 12;

    // Adjust horizontal position - try right side first, then left side, then clamp
    if (left + overlayRect.width > window.innerWidth - margin) {
      left = cellRect.left - overlayRect.width - offset;
    }
    left = Math.max(margin, left);

    // Adjust vertical position - clamp to viewport bounds
    top = Math.max(margin, Math.min(window.innerHeight - overlayRect.height - margin, top));

    return { left, top };
  }

  /**
   * Formats timestamp with optional prefix
   */
  function formatTimestamp(includePrefix = true) {
    const timestamp = new Date().toISOString();
    return includePrefix ? `${timestamp} ` : timestamp;
  }

  /**
   * Enhanced element creation utility
   */
  function createElement(tagName, options = {}) {
    const element = document.createElement(tagName);
    if (options.id) element.id = options.id;
    if (options.className) element.className = options.className;
    if (options.textContent) element.textContent = options.textContent;
    if (options.styles) Object.assign(element.style, options.styles);
    if (options.attributes) {
      Object.entries(options.attributes).forEach(([key, value]) => {
        element.setAttribute(key, value);
      });
    }
    if (options.parent) options.parent.appendChild(element);
    return element;
  }

  /**
   * Adds multiple event listeners to an element
   */
  function addEventListeners(element, eventMap) {
    Object.entries(eventMap).forEach(([event, handler]) => {
      element.addEventListener(event, handler);
    });
  }

  /**
   * Snaps a value to the nearest grid position
   */
  function snapToGrid(value, snapSize) {
    return Math.round(value / snapSize) * snapSize;
  }

  /**
   * Validates if an element exists and is valid
   */
  function isValidElement(element) {
    return element && element.nodeType === Node.ELEMENT_NODE;
  }

  /**
   * Creates a timeout with optional cleanup of existing timeout
   */
  function createTimeout(callback, delay, existingTimeout = null) {
    if (existingTimeout) {
      clearTimeout(existingTimeout);
    }
    return setTimeout(callback, delay);
  }

  /**
   * Creates a Promise that resolves after the specified delay
   */
  function delay(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  /**
   * Unified API fetch utility with consistent error handling
   */
  async function apiCall(url, options = {}) {
    const defaultOptions = {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    };

    const requestOptions = { ...defaultOptions, ...options };

    try {
      const response = await fetch(url, requestOptions);

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`HTTP ${response.status}: ${errorText}`);
      }

      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        return await response.json();
      } else {
        return await response.text();
      }
    } catch (error) {
      handleFetchError(error, `API call to ${url}`);
      throw error;
    }
  }

  /**
   * Unified overlay creation with consistent styling
   */
  function createOverlay(className, options = {}) {
    const overlay = createElement('div', {
      className,
      styles: {
        position: 'fixed',
        background: 'rgba(10,20,40,0.98)',
        color: '#a7ecff',
        zIndex: '10000',
        padding: '8px 12px',
        borderRadius: '4px',
        fontFamily: 'monospace',
        fontSize: '14px',
        maxWidth: '350px',
        wordWrap: 'break-word',
        ...options.styles,
      },
    });

    if (options.parent) {
      options.parent.appendChild(overlay);
    } else {
      document.body.appendChild(overlay);
    }

    return overlay;
  }

  // --- State ---
  let hoveredCellId = null; // ID of the currently hovered cell ('cell-R-C')
  let gridCellEventSource = null; // EventSource instance
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
    mousePositionElement = createElement('div', {
      id: 'mouse-position',
      styles: config.ui.mousePositionStyles,
      parent: gridPositionElement.parentNode,
    });
    gridPositionElement.parentNode.insertBefore(mousePositionElement, gridPositionElement);
  }

  function updateMousePositionDisplay(x, y) {
    if (mousePositionElement) {
      mousePositionElement.textContent = `Mouse Position: row/Y=${y}, col/X=${x}`;
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
  let mouseGridPosition = { row: 0, col: 0 };
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
      const clamped = clampToGridBounds(gridX, gridY);
      gridX = clamped.x;
      gridY = clamped.y;
      mouseGridPosition.row = gridY;
      mouseGridPosition.col = gridX;
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
    return getCellStatusFromElement(cellElement);
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
      createElement('div', {
        id: 'command-status',
        textContent: 'Type number + x/y/h/j/k/l to navigate (e.g., 100x, 50h, 30j)',
        parent: infoPanel,
      });
    }
  }

  /**
   * Updates the grid position display in the info panel
   */
  function updateGridPositionDisplay() {
    const gridPositionElement = document.getElementById('grid-position');
    if (gridPositionElement) {
      gridPositionElement.textContent = `Grid Position: row/Y=${viewportY}, col/X=${viewportX}`;
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
    const availableWidth = Math.max(config.grid.cellMinSize, window.innerWidth - 2 * sideMargin);
    const availableHeight = Math.max(config.grid.cellMinSize, window.innerHeight - infoPanelHeight - 2 * sideMargin);

    // Calculate number of cells that can fit (accounting for gap between cells)
    gridCols = Math.max(1, Math.floor(availableWidth / (config.grid.cellMinSize + config.grid.cellGap)));
    gridRows = Math.max(1, Math.floor(availableHeight / (config.grid.cellMinSize + config.grid.cellGap)));

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
    const totalGapHeight = (gridRows - 1) * config.grid.cellGap;
    const cellHeight = (gridContentHeight - totalGapHeight) / gridRows;

    // Get grid container's computed style
    const gridStyle = window.getComputedStyle(gridContainer);
    const topPadding = parseInt(gridStyle.paddingTop, 10) || 0;
    const borderWidth = parseInt(gridStyle.borderTopWidth, 10) || 0;
    const gapSize = config.grid.cellGap;

    // Create ticks and labels for each row
    for (let r = 0; r < gridRows; r++) {
      // Create tick mark
      const tick = document.createElement('div');
      tick.classList.add('axis-tick', 'left-tick');

      // Add special classes for multiples of 5 and 10
      if (r % config.ui.axisTickIntervalMajor === 0) {
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
      } else if (r % config.ui.axisTickIntervalMinor === 0) {
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
    const gapSize = config.grid.cellGap;

    // Create ticks and labels for each column
    for (let c = 0; c < gridCols; c++) {
      // Create tick mark
      const tick = document.createElement('div');
      tick.classList.add('axis-tick', 'bottom-tick');

      // Add special classes for multiples of 5 and 10
      if (c % config.ui.axisTickIntervalMajor === 0) {
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
      } else if (c % config.ui.axisTickIntervalMinor === 0) {
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
   * Sets up overlay hover events for a cell
   */
  function setupCellOverlayEvents(cell) {
    let hoverTimer = null;

    addEventListeners(cell, {
      mouseenter: () => {
        removeGridCellOverlay();
        // Only show overlay for cells with elapsed time data
        if (!cellHasElapsedTime(cell)) {
          return;
        }
        hoverTimer = createTimeout(async () => {
          // Double-check class in case cell state changed during delay
          if (!cellHasElapsedTime(cell)) return;
          fetchTimingOverlayData();
        }, config.timing.overlayHoverDelay);
      },
      mouseleave: () => {
        clearTimeout(hoverTimer);
        removeGridCellOverlay();
      },
    });
  }

  /**
   * Sets up hover tracking events for a cell
   */
  function setupCellHoverTracking(cell) {
    addEventListeners(cell, {
      mouseenter: () => {
        hoveredCellId = cell.id;
        if (selectionMode && selectionStart) {
          selectionEnd = getCellCoordinates(cell.id);
          updateSelectionPreview();
        }
      },
      mouseleave: () => {
        if (hoveredCellId === cell.id) {
          hoveredCellId = null;
        }
      },
    });
  }

  /**
   * Sets up selection events for a cell
   */
  function setupCellSelectionEvents(cell) {
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
  }

  /**
   * Creates a single grid cell with all event handlers
   */
  function createGridCell(row, col) {
    const cell = createElement('div', {
      className: 'grid-cell',
    });

    // Calculate the actual grid coordinates based on viewport position
    const actualRow = row + viewportY;
    const actualCol = col + viewportX;
    cell.id = createCellId(actualRow, actualCol);

    // Set up all event handlers
    setupCellOverlayEvents(cell);
    setupCellHoverTracking(cell);
    setupCellSelectionEvents(cell);

    return cell;
  }

  /**
   * Creates all grid cells and adds them to the container
   */
  function createGridCells() {
    for (let row = 0; row < gridRows; row++) {
      for (let col = 0; col < gridCols; col++) {
        const cell = createGridCell(row, col);
        gridContainer.appendChild(cell);
      }
    }
    logInfo(`Grid created with ${gridRows}x${gridCols} cells.`);
  }

  /**
   * Applies CSS grid styling to the container
   */
  function applyGridStyling() {
    gridContainer.style.gridTemplateColumns = `repeat(${gridCols}, minmax(${config.grid.cellMinSize}px, 1fr))`;
    gridContainer.style.gridTemplateRows = `repeat(${gridRows}, minmax(${config.grid.cellMinSize}px, 1fr))`;
  }

  /**
   * Resets cell counters and updates display
   */
  function resetCellCounts() {
    cellCounts = {
      total: 0,
      red: 0,
      green: 0,
      blue: 0,
      orange: 0,
      predator: 0,
    };
    updateGridSummary();
  }

  /**
   * Creates grid axes after a short delay
   */
  function createGridAxes() {
    createTimeout(() => {
      createLeftAxis();
      createBottomAxis();
    }, 0);
  }

  /**
   * Main grid creation function - orchestrates the grid building process
   */
  function createGrid() {
    // Initialize
    removeGridCellOverlay();
    calculateGridDimensions();
    gridContainer.innerHTML = '';

    // Build grid
    createGridCells();
    applyGridStyling();
    resetCellCounts();
    createGridAxes();
  }

  /**
   * Sends the update command to the backend via HTTP PUT.
   * @param {string} id The cell's entity ID (e.g., "RxC" format)
   * @param {string} colorChar The action key ('r', 'g', 'b', 'd')
   * @param {string} commandPath The command path to send ('create-shape', 'clear-cells', 'erase-cells')
   * @param {number} radius The radius for span and fill commands
   */
  async function sendCellUpdate(id, colorChar, commandPath, radius = 0, width = 0, height = 0) {
    // Use 'RxC' as the service ID format (no conversion needed)
    const serverFormatId = id;
    const apiUrl = `${origin}/grid-cell/${commandPath}`;
    const status = config.colors.statusMap[colorChar];
    const { x: centerX, y: centerY } = parseCoordinatesFromId(id);
    const maxRetries = config.retry.maxAttempts;
    const retryDelay = config.retry.delay; // ms
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
          body: JSON.stringify(
            commandPath === 'create-shape'
              ? {
                  id: serverFormatId,
                  status: status,
                  clientAt: clientAt,
                  locationX: centerX,
                  locationY: centerY,
                  radius: radius,
                  width: width,
                  height: height,
                }
              : {
                  id: serverFormatId,
                  status: status,
                  clientAt: clientAt,
                  centerX: centerX,
                  centerY: centerY,
                  radius: radius,
                }
          ),
        });

        if (response.ok) {
          console.info(`${new Date().toISOString()} `, `Update request for ${id} sent successfully.`);
          success = true;
        } else {
          const errorText = await response.text();
          lastError = `HTTP error for ${id}! Status: ${response.status} ${errorText}`;
          console.error(`${new Date().toISOString()} `, lastError);
          if (attempt < maxRetries) {
            await delay(retryDelay);
          }
        }
      } catch (error) {
        lastError = error;
        handleFetchError(error, `sending cell ${id} update`);
        if (attempt < maxRetries) {
          await delay(retryDelay);
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
        centerX: parseCoordinatesFromId(id).x,
        centerY: parseCoordinatesFromId(id).y,
        radius: range,
      }),
    });
    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`);
    }
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
        const cellId = createCellId(...gridCell.id.split('x').map((n) => parseInt(n)));
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

            // Update cell status and elapsed time
            updateCellStatus(gridCellElement, gridCell);
            updateCellElapsedTime(gridCellElement, gridCell);

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

    const regions = subdivideGrid(viewportY, viewportX, gridRows, gridCols, config.grid.maxCellsPerRegion);

    for (const region of regions) {
      await queryGridCellData(region, 'start');
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
    const commandMap = {
      x: (value) => ({ x: value }), // Absolute X position
      y: (value) => ({ y: value }), // Absolute Y position
      h: (value) => ({ relativeX: -value }), // Move left
      j: (value) => ({ relativeY: value }), // Move down
      k: (value) => ({ relativeY: -value }), // Move up
      l: (value) => ({ relativeX: value }), // Move right
    };

    return commandMap[lastChar]?.(value) || null;
  }

  /**
   * Updates the viewport position and refreshes the grid
   * @param {number} x - New absolute X coordinate (optional)
   * @param {number} y - New absolute Y coordinate (optional)
   * @param {number} relativeX - Relative X movement (optional)
   * @param {number} relativeY - Relative Y movement (optional)
   */
  function updateViewport(x, y, relativeX, relativeY) {
    let newX = viewportX;
    let newY = viewportY;

    // Handle absolute X positioning
    if (x !== undefined) {
      newX = snapToGrid(x, config.grid.positionSnap);
    }

    // Handle absolute Y positioning
    if (y !== undefined) {
      newY = snapToGrid(y, config.grid.positionSnap);
    }

    // Handle relative X movement (h/l commands)
    if (relativeX !== undefined) {
      newX = viewportX + snapToGrid(relativeX, config.grid.positionSnap);
    }

    // Handle relative Y movement (j/k commands)
    if (relativeY !== undefined) {
      newY = viewportY + snapToGrid(relativeY, config.grid.positionSnap);
    }

    // Clamp values to grid boundaries
    const clamped = clampToGridBounds(newX, newY);
    const clampedX = clamped.x;
    const clampedY = clamped.y;

    // Check if position actually changed
    const xChanged = viewportX !== clampedX;
    const yChanged = viewportY !== clampedY;
    const changed = xChanged || yChanged;

    if (xChanged) viewportX = clampedX;
    if (yChanged) viewportY = clampedY;

    if (changed) {
      // Update the grid position display
      updateGridPositionDisplay();

      // Refresh the grid
      createGrid();

      // Show a notification
      updateCommandStatus(`Viewport moved to x:${viewportX}, y:${viewportY}`, 2000);
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
        createTimeout(() => {
          commandStatus.textContent = 'Type number + x/y/h/j/k/l to navigate (e.g., 100x, 50h, 30j)';
        }, timeout);
      }
    }
  }

  /**
   * Handles selection mode toggle with Shift key
   */
  function handleSelectionModeToggle(event) {
    if (event.key === 'Shift' && !event.repeat) {
      selectionMode = true;
      document.body.classList.add('selection-active');
      updateSelectionStatus('Selection mode active - Click and drag to select cells');
      return true;
    }
    return false;
  }

  /**
   * Handles navigation command input (numbers, vim movements)
   */
  function handleNavigationCommand(event) {
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
        // Set a timeout to clear the command buffer if no key is pressed
        commandTimeout = createTimeout(() => {
          commandBuffer = '';
          updateCommandStatus('Command timeout. Type number + x/y to navigate', 2000);
        }, config.timing.commandTimeout);
      }

      return true;
    }
    return false;
  }

  /**
   * Handles command buffer escape/cancel
   */
  function handleCommandCancel(event) {
    if (commandBuffer.length > 0 && event.key === 'Escape') {
      commandBuffer = '';
      updateCommandStatus('Command canceled', 2000);
      return true;
    }
    return false;
  }

  /**
   * Gets the current cell status color character
   */
  function getCellColorChar(cellElement) {
    return getCellColorCharFromElement(cellElement);
  }

  /**
   * Handles color key commands (r, g, b, o, d)
   */
  function handleColorCommand(event) {
    if (['r', 'g', 'b', 'o', 'd'].includes(event.key.toLowerCase())) {
      event.preventDefault();
      const colorChar = event.key.toLowerCase();
      const radius = commandBuffer.length == 0 ? 0 : parseInt(commandBuffer);
      const commandPath = 'create-shape';

      if (currentSelection.length > 0) {
        // Rectangle selection mode
        const { x: topLeftX, y: topLeftY } = parseCoordinatesFromId(currentSelection[0]);
        const { x: bottomRightX, y: bottomRightY } = parseCoordinatesFromId(currentSelection[currentSelection.length - 1]);
        const width = Math.abs(bottomRightX - topLeftX) + 1;
        const height = Math.abs(bottomRightY - topLeftY) + 1;
        const id = topLeftY + 'x' + topLeftX;
        sendCellUpdate(id, colorChar, commandPath, 0, width, height);

        clearSelection();
        updateSelectionStatus('Color applied to selection');
      } else if (hoveredCellId) {
        const id = extractCellId(hoveredCellId);
        sendCellUpdate(id, colorChar, commandPath, radius);
      }
      return true;
    }
    return false;
  }

  /**
   * Handles cell clear command (c)
   */
  function handleClearCommand(event) {
    if (event.key === 'c') {
      event.preventDefault();
      const cellElement = document.getElementById(hoveredCellId);
      const hasElapsedTime = cellHasElapsedTime(cellElement);

      if (hasElapsedTime) {
        const commandPath = 'clear-cells';
        const id = extractCellId(hoveredCellId);
        const colorChar = getCellColorCharFromElement(cellElement);
        if (colorChar.length > 0) {
          sendCellUpdate(id, colorChar, commandPath);
        }
      }
      return true;
    }
    return false;
  }

  /**
   * Handles cell erase command (e)
   */
  function handleEraseCommand(event) {
    if (event.key === 'e') {
      event.preventDefault();
      const cellElement = document.getElementById(hoveredCellId);
      const hasElapsedTime = cellElement && cellElement.classList.contains('has-elapsed-time');

      if (hasElapsedTime) {
        const commandPath = 'erase-cells';
        const id = extractCellId(hoveredCellId);
        sendCellUpdate(id, '', commandPath);
      }
      return true;
    }
    return false;
  }

  /**
   * Handles overlay commands (q, t, p)
   */
  function handleOverlayCommands(event) {
    // Cell data details command
    if (event.key === 'q') {
      event.preventDefault();
      if (hoveredCellId) {
        const cellElement = document.getElementById(hoveredCellId);
        if (cellHasElapsedTime(cellElement)) {
          fetchGridCellOverlayData(cellElement);
        }
      }
      return true;
    }

    // Timings command
    if (event.key === 't') {
      event.preventDefault();
      const cellElement = document.getElementById(hoveredCellId);

      if (cellHasElapsedTime(cellElement)) {
        fetchTimingOverlayData();
      }
      return true;
    }

    // Predator command
    if (event.key === 'p') {
      event.preventDefault();
      if (hoveredCellId) {
        const id = extractCellId(hoveredCellId);
        const range = commandBuffer.length == 0 ? 0 : parseInt(commandBuffer);
        sendCreatePredator(id, range);
      }
      return true;
    }

    return false;
  }

  /**
   * Handles system commands (m, a)
   */
  function handleSystemCommands(event) {
    // Toggle microphone
    if (event.key === 'm') {
      event.preventDefault();
      voiceCommand.toggleMicrophone();
      return true;
    }

    // Toggle agent message viewer
    if (event.key === 'a') {
      event.preventDefault();
      toggleAgentMessageOverlay();
      return true;
    }

    return false;
  }

  /**
   * Handles agent overlay navigation
   */
  function handleAgentOverlayNavigation(event) {
    if (document.querySelector('.agent-message-overlay')) {
      if (event.key === 'ArrowUp') {
        event.preventDefault();
        navigateAgentMessages(-1);
        return true;
      } else if (event.key === 'ArrowDown') {
        event.preventDefault();
        navigateAgentMessages(1);
        return true;
      } else if (event.key === 'Escape') {
        event.preventDefault();
        removeAgentMessageOverlay();
        return true;
      }
    }
    return false;
  }

  /**
   * Main keyboard event dispatcher - delegates to specific handlers
   * @param {KeyboardEvent} event
   */
  function handleGlobalKeyDown(event) {
    const keyHandlers = [
      handleSelectionModeToggle,
      handleNavigationCommand,
      handleCommandCancel,
      handleColorCommand,
      handleClearCommand,
      handleEraseCommand,
      handleOverlayCommands,
      handleSystemCommands,
      handleAgentOverlayNavigation,
    ];

    // Try each handler until one successfully handles the event
    keyHandlers.some((handler) => handler(event));
  }

  /**
   * Fetches and shows grid cell data for the hovered cell.
   * @param {HTMLElement} cellElement - The cell element to show overlay for
   */
  async function fetchGridCellOverlayData(cellElement) {
    // Fetch and show grid cell data for the hovered cell
    const id = hoveredCellId.substring(5); // Remove "cell-" prefix
    try {
      const data = await apiCall(`${origin}${config.endpoints.gridCellViewById}/${id}`);
      showGridCellOverlay(cellElement, data);
    } catch (error) {
      // Error already handled by apiCall
    }
  }

  /**
   * Shows a grid overlay with grid cell data on the given cell.
   * @param {HTMLElement} cell
   * @param {Object} data
   */
  function showGridCellOverlay(cell, data) {
    removeGridCellOverlay();
    const overlay = createOverlay('grid-cell-overlay', {
      styles: {
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        fontSize: '12px',
        border: '2px solid #be43a4',
        borderRadius: '7px',
        boxShadow: '0 0 16px #be43a4',
        padding: '14px 18px',
        pointerEvents: 'none',
        maxWidth: config.overlay.maxWidth + 'px',
        maxHeight: config.overlay.maxHeightVh + 'vh',
        overflowY: 'auto',
      },
    });

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
    const initialLeft = cellRect.right + 12;
    const initialTop = cellRect.top;

    const { left, top } = clampOverlayPosition(initialLeft, initialTop, overlayRect, cellRect);
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
  async function fetchTimingOverlayData() {
    const id = hoveredCellId.substring(5); // Remove "cell-" prefix
    const cellElement = document.getElementById(hoveredCellId);

    try {
      const routes = await getRoutes();
      console.info(`${new Date().toISOString()} `, `Multi-region routes ${routes}`);

      const dataPromises = routes.map(async (route, idx) => {
        const routeUrl = buildRouteUrl(route, id);
        console.info(`${new Date().toISOString()} `, `Timings for region ${routeUrl}`);

        try {
          const data = await apiCall(routeUrl);
          return data;
        } catch (error) {
          console.warn(`${new Date().toISOString()} `, `Error fetching route data: ${error}`);
          return null;
        }
      });

      const dataList = await Promise.all(dataPromises);
      showTimingOverlay(dataList, cellElement);
    } catch (error) {
      console.warn(`${new Date().toISOString()} `, `Error fetching routes: ${error}`);
    }
  }

  /**
   * Builds the URL for fetching route data
   */
  function buildRouteUrl(route, id) {
    const protocol = route.startsWith('localhost') || route.startsWith('127.0.0.1') ? 'http' : 'https';
    return `${protocol}://${route}/grid-cell/view-row-by-id/${id}`;
  }

  /**
   * Gets routes either from URL query parameter or by fetching from the server
   * @returns {Promise<Array>} Promise that resolves to an array of routes
   */
  async function getRoutes() {
    // First check for a "routes" query parameter
    const urlParams = new URLSearchParams(window.location.search);
    const routesParam = urlParams.get('routes');

    if (routesParam) {
      // If routes parameter exists, parse it (assuming comma-separated list)
      const routes = routesParam.split(',');
      console.info(`${new Date().toISOString()} `, `Using routes from URL parameter: ${routes}`);
      return routes;
    } else {
      // Otherwise fetch routes from the server
      try {
        const routes = await apiCall(config.endpoints.gridCellMultiRegionRoutes);
        console.info(`${new Date().toISOString()} `, `Fetched multi-region routes: ${routes}`);
        return routes;
      } catch (error) {
        console.warn(`${new Date().toISOString()} `, `Error fetching routes: ${error}`);
        throw error;
      }
    }
  }

  /**
   * Processes and validates timing data
   */
  function processTimingData(dataList) {
    const validData = dataList.filter((d) => d && d.viewAt && d.endpointAt && d.updatedAt);
    if (!validData.length) return null;

    const parsed = validData.map((d) => {
      const obj = { ...d };
      for (const k in obj) {
        if (k.endsWith('At') && obj[k]) obj[k] = new Date(obj[k]);
      }
      return obj;
    });

    parsed.sort((a, b) => a.viewAt - b.viewAt);
    return parsed;
  }

  /**
   * Calculates timing values for visualization
   */
  function calculateTimingValues(parsed) {
    const endpointAt0 = parsed[0].endpointAt;
    const updatedAt = parsed[0].updatedAt;
    const youngestViewAt = parsed[parsed.length - 1].viewAt;
    const oldestViewAt = parsed[0].viewAt;
    const gap1 = updatedAt - endpointAt0;
    const gap2 = youngestViewAt - updatedAt;

    // Compensate for excess endpoint to entity elapsed time
    const endpointAt = gap1 > gap2 ? new Date(updatedAt - gap2) : endpointAt0;
    const msRange = youngestViewAt - endpointAt;

    return {
      endpointAt0,
      endpointAt,
      updatedAt,
      youngestViewAt,
      oldestViewAt,
      msRange,
    };
  }

  /**
   * Creates the basic overlay container
   */
  function createTimingOverlayContainer(pxWidth, pxHeight) {
    return createOverlay('grid-cell-overlay', {
      styles: {
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        fontSize: '12px',
        padding: '16px 24px',
        border: '2px solid #be43a4',
        borderRadius: '7px',
        boxShadow: '0 0 16px #be43a4',
        minWidth: pxWidth + 40 + 'px',
        minHeight: pxHeight + 20 + 'px',
        maxWidth: '90vw',
        maxHeight: '80vh',
      },
    });
  }

  /**
   * Creates a table row for timing data
   */
  function createTimingTableRow(idx, key, value, region) {
    const row = document.createElement('tr');

    const cells = [
      { content: key, styles: { padding: '2px 6px', fontWeight: 'bold', textAlign: 'right', color: '#6fffc8' } },
      { content: value, styles: { padding: '2px 6px', textAlign: 'left', color: '#ffffff' } },
      { content: region, styles: { padding: '2px 6px', textAlign: 'left', color: '#e7bf50' } },
      { content: idx, styles: { padding: '2px 6px', textAlign: 'center', color: '#ffffff' } },
    ];

    cells.forEach(({ content, styles }) => {
      const cell = document.createElement('td');
      cell.textContent = content;
      Object.assign(cell.style, styles);
      row.appendChild(cell);
    });

    return row;
  }

  /**
   * Creates the timing data table
   */
  function createTimingTable(parsed) {
    const table = document.createElement('table');
    table.style.marginBottom = '10px';
    table.style.borderCollapse = 'collapse';

    const p = parsed[0];
    table.appendChild(createTimingTableRow('', 'ID', p.id, p.updated));
    table.appendChild(createTimingTableRow('', 'Endpoint to entity', `${p.updatedAt - p.endpointAt} ms`, p.updated));
    table.appendChild(createTimingTableRow('1', 'Entity to view', `${p.viewAt - p.updatedAt} ms`, p.view));

    for (let i = 1; i < parsed.length; i++) {
      const p = parsed[i];
      table.appendChild(createTimingTableRow(`${i + 1}`, 'Entity to view', `${p.viewAt - p.updatedAt} ms`, p.view));
    }

    return table;
  }

  /**
   * Creates the SVG container for timing visualization
   */
  function createTimingSVG(pxWidth, pxHeight) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('width', pxWidth);
    svg.setAttribute('height', pxHeight);

    Object.assign(svg.style, {
      display: 'block',
      background: 'rgba(20,30,60,0.9)',
      borderRadius: '6px',
      marginBottom: '8px',
    });

    return svg;
  }

  /**
   * Draws the main timeline on the SVG
   */
  function drawMainTimeline(svg, timingValues, pxWidth, pxIndent, yStep) {
    const { endpointAt0, endpointAt, updatedAt, oldestViewAt, msRange } = timingValues;

    const msToX = (ms) => {
      if (msRange === 0) return pxIndent;
      return Math.round(((ms - endpointAt) / msRange) * (pxWidth - 2 * pxIndent)) + pxIndent;
    };

    const y0 = yStep;
    const xEndpoint = msToX(endpointAt);
    const xUpdated = msToX(updatedAt);
    const xOldestView = msToX(oldestViewAt);

    // Draw lines
    const color = endpointAt0 == endpointAt ? '#a7ecff' : '#f8f53f';
    svg.appendChild(svgLine(xEndpoint, y0, xUpdated, y0, color));
    svg.appendChild(svgLine(xUpdated, y0, xOldestView, y0, '#a7ecff'));

    // Draw markers
    const markerColors = ['#44ddff', '#ff4d6f', '#ffd24d'];
    [xEndpoint, xUpdated, xOldestView].forEach((x, i) => {
      svg.appendChild(svgCircle(x, y0, 5, markerColors[i], '#222', '1'));
    });

    svg.appendChild(svgText(xOldestView, y0 - 12, '1', '15', 'bold', '#ffffff'));

    return msToX;
  }

  /**
   * Draws additional region lines on the SVG
   */
  function drawAdditionalRegions(svg, parsed, msToX, yStep, updatedAt) {
    for (let i = 1; i < parsed.length; i++) {
      const y = yStep * (i + 1);
      const xStart = msToX(updatedAt);
      const xEnd = msToX(parsed[i].viewAt);

      // Draw timing lines
      svg.appendChild(svgLine(xStart, y, xEnd, y, '#44ddff'));
      svg.appendChild(svgLine(xStart, y - yStep + 7, xStart, y, '#44ddff'));

      // Draw markers
      svg.appendChild(svgCircle(xStart, y, 5, '#ff4d6f', '#222', '1'));
      svg.appendChild(svgCircle(xEnd, y, 5, '#ffd24d', '#222', '1'));
      svg.appendChild(svgText(xEnd, y - 12, i + 1, '15', 'bold', '#ffffff'));
    }
  }

  /**
   * Positions the overlay relative to the cell
   */
  function positionOverlay(overlay, cellElement) {
    document.body.appendChild(overlay);
    const cellRect = cellElement.getBoundingClientRect();
    const overlayRect = overlay.getBoundingClientRect();

    const initialLeft = cellRect.right + 12;
    const initialTop = cellRect.top - 8;

    const { left, top } = clampOverlayPosition(initialLeft, initialTop, overlayRect, cellRect);
    overlay.style.left = left + 'px';
    overlay.style.top = top + 'px';
    cellElement.classList.add('grid-cell-overlay-active');
  }

  /**
   * Sets up overlay dismiss handlers
   */
  function setupOverlayDismiss(overlay) {
    function onDismiss(e) {
      const isEscapeKey = e.type === 'keydown' && e.key === 'Escape';
      const isClickOutside = e.type === 'mousedown' && !overlay.contains(e.target);

      if (isEscapeKey || isClickOutside) {
        removeGridCellOverlay();
        document.removeEventListener('mousedown', onDismiss, true);
        document.removeEventListener('keydown', onDismiss, true);
      }
    }

    createTimeout(() => {
      document.addEventListener('mousedown', onDismiss, true);
      document.addEventListener('keydown', onDismiss, true);
    }, 0);
  }

  /**
   * Main function to display the timing overlay for a cell
   * @param {Array} dataList - List of timing data objects from all routes
   * @param {HTMLElement} cellElement - The cell element to show overlay for
   */
  function showTimingOverlay(dataList, cellElement) {
    removeGridCellOverlay();

    const parsed = processTimingData(dataList);
    if (!parsed) return;

    const timingValues = calculateTimingValues(parsed);
    const pxWidth = config.overlay.timingWidth;
    const pxIndent = config.overlay.timingIndent;
    const pxHeight = Math.max(config.overlay.timingMinHeight, parsed.length * config.overlay.timingRowHeight);

    // Create overlay components
    const overlay = createTimingOverlayContainer(pxWidth, pxHeight);
    const table = createTimingTable(parsed);
    const svg = createTimingSVG(pxWidth, pxHeight);

    // Draw timing visualization
    const yStep = pxHeight / (parsed.length + 1);
    const msToX = drawMainTimeline(svg, timingValues, pxWidth, pxIndent, yStep);
    drawAdditionalRegions(svg, parsed, msToX, yStep, timingValues.updatedAt);

    // Assemble and position overlay
    overlay.appendChild(table);
    overlay.appendChild(svg);
    positionOverlay(overlay, cellElement);
    setupOverlayDismiss(overlay);
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
    const parsed = parseCellId(cellId);
    if (!parsed) {
      console.error('Invalid cell coordinates:', cellId);
      return { row: 0, col: 0 };
    }
    return parsed;
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
        const cellId = createCellId(r, c);
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

    // Hide after timeout if empty message
    if (!message) {
      createTimeout(() => {
        statusElement.textContent = '';
      }, config.timing.selectionClearTimeout);
    }
  }

  /**
   * Initialize the viewport position to default values
   */
  async function initializeViewport() {
    // Set default viewport position (0,0)
    viewportX = 0;
    viewportY = 0;

    // Update the URL display to show the viewport position
    // Fetch region name from backend and display
    try {
      const regionName = await apiCall(`${origin}${config.endpoints.gridCellRegion}`);
      regionNameSpan.textContent = regionName.trim();
    } catch (error) {
      regionNameSpan.textContent = 'local-development';
    }
  }

  // Fetch and display project version
  // Run bash script version-to-static.sh to update version.txt
  (async () => {
    try {
      const version = await apiCall('version.txt');
      document.getElementById('project-version').textContent = `Version: ${version.trim()}`;
    } catch (error) {
      document.getElementById('project-version').textContent = 'Version not found';
    }
  })();

  class VoiceCommand {
    constructor() {
      this.recordButton = null;

      this.mediaRecorder = null;
      this.audioChunks = [];
      this.isRecording = false;

      this.init();
    }

    async init() {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        this.mediaRecorder = new MediaRecorder(stream);

        this.createRecordButton();
        this.setupEventListeners();
        // this.updateStatus('Ready to listen');
      } catch (error) {
        // this.showError('Microphone access denied. Please allow microphone access to use voice chat.');
        console.error(`${new Date().toISOString()} `, 'Error accessing microphone:', error);
      }
    }

    createRecordButton() {
      const recordButton = document.createElement('div');
      recordButton.id = 'record-button';
      recordButton.className = 'record-button';
      recordButton.innerHTML = '<div class="record-icon"></div>';
      document.body.appendChild(recordButton);
      this.recordButton = recordButton;
    }

    setupEventListeners() {
      this.recordButton.addEventListener('click', () => {
        if (this.isRecording) {
          this.stopRecording();
        } else {
          this.startRecording();
        }
      });

      this.mediaRecorder.addEventListener('dataavailable', (event) => {
        this.audioChunks.push(event.data);
      });

      this.mediaRecorder.addEventListener('stop', () => {
        this.processAudio();
      });
    }

    startRecording() {
      this.audioChunks = [];
      this.mediaRecorder.start();
      this.isRecording = true;

      this.recordButton.classList.add('recording');
      // this.updateStatus('Recording...', 'recording');
    }

    stopRecording() {
      this.mediaRecorder.stop();
      this.isRecording = false;

      this.recordButton.classList.remove('recording');
      this.recordButton.disabled = true;
      // this.updateStatus('Processing...', 'processing');
    }

    toggleMicrophone() {
      if (this.mediaRecorder.state === 'recording') {
        this.stopRecording();
      } else {
        this.startRecording();
      }
    }

    async processAudio() {
      const audioBlob = new Blob(this.audioChunks, { type: 'audio/wav' });
      const formData = new FormData();
      formData.append('audio', audioBlob, 'voice-command.wav');

      console.info(`${new Date().toISOString()} `, 'Processing audio...');
      updateCommandStatus('Processing voice command...', 0);

      try {
        const responseText = await apiCall(`${origin}${config.endpoints.agentVoiceCommand}`, {
          method: 'POST',
          headers: {
            // Custom headers for viewport information and user session ID
            'X-Viewport-Top-Left-Row': viewportY,
            'X-Viewport-Top-Left-Col': viewportX,
            'X-Viewport-Bottom-Right-Row': viewportY + gridRows,
            'X-Viewport-Bottom-Right-Col': viewportX + gridCols,
            'X-Mouse-Row': viewportY + mouseGridPosition.row, // Set relative position to grid absolute position
            'X-Mouse-Col': viewportX + mouseGridPosition.col, // Set relative position to grid absolute position
            'X-User-Session-Id': getSessionId(),
          },
          body: formData,
        });

        console.info(`${new Date().toISOString()} `, 'Audio processed: LLM agent response:', responseText);
        updateCommandStatus('Voice command received: ' + responseText, 5000);
        // The actual processing of commands will happen through the agent step stream
        // No need to parse the response here as we'll get updates via SSE
      } catch (error) {
        updateCommandStatus('Error processing voice command', 5000);
      } finally {
        // Re-enable the record button
        this.recordButton.disabled = false;
      }
    }

    processLLMResponse(responseJson) {
      console.debug(`${new Date().toISOString()} `, 'Audio processed: LLM agent response:', responseJson);
      if (!Array.isArray(responseJson)) return;

      responseJson.forEach((command) => {
        console.debug(`${new Date().toISOString()} `, 'Processing command:', command);
        const parsedCommand = this.parseCommand(command);
        if (parsedCommand) {
          this.processLLMCommand(parsedCommand);
        }
      });
    }

    parseCommand(command) {
      if (typeof command === 'string') {
        try {
          return JSON.parse(command);
        } catch (e) {
          console.error(`${new Date().toISOString()} `, 'Error parsing command JSON:', e);
          return null;
        }
      }
      return command;
    }

    processLLMCommand(command) {
      if (!command || !command.tool) {
        console.warn(`${new Date().toISOString()} `, 'Invalid command format:', command);
        return;
      }

      console.debug(`${new Date().toISOString()} `, 'Processing command tool:', command.tool);
      switch (command.tool) {
        case 'absoluteViewportNavigation':
          this.absoluteViewportNavigation(command);
          break;
        case 'relativeViewportNavigation':
          this.relativeViewportNavigation(command);
          break;
        default:
          console.info(`${new Date().toISOString()} `, 'Command will be handled by backend:', command.tool);
          // Other commands like drawing cells, rectangles, etc. are handled by the backend
          break;
      }
    }

    absoluteViewportNavigation(command) {
      const newX = command.parameters.col;
      if (newX != viewportX) {
        const parsedCommand = parseViewportCommand(`${newX}x`);
        if (parsedCommand) {
          updateViewport(parsedCommand.x, parsedCommand.y, parsedCommand.relativeX, parsedCommand.relativeY);
        }
      }
      const newY = command.parameters.row;
      if (newY != viewportY) {
        const parsedCommand = parseViewportCommand(`${newY}y`);
        if (parsedCommand) {
          updateViewport(parsedCommand.x, parsedCommand.y, parsedCommand.relativeX, parsedCommand.relativeY);
        }
      }
    }

    relativeViewportNavigation(command) {
      const direction = command.parameters.direction;
      const amount = command.parameters.amount;
      let moveCommand = '';

      const directionMap = {
        left: 'h',
        right: 'l',
        up: 'k',
        down: 'j',
      };

      if (directionMap[direction]) {
        moveCommand = `${amount}${directionMap[direction]}`;
      } else {
        console.error(`${new Date().toISOString()} `, 'Audio processed: LLM agent response command:', command);
      }

      const parsedCommand = parseViewportCommand(moveCommand);
      if (parsedCommand) {
        updateViewport(parsedCommand.x, parsedCommand.y, parsedCommand.relativeX, parsedCommand.relativeY);
      }
    }
  }
  const voiceCommand = new VoiceCommand();

  // --- Initialization ---
  await initializeViewport(); // Set default viewport position
  createCommandDisplay(); // Add command status display to the info panel
  updateGridPositionDisplay(); // Update grid position display
  createGrid();
  fetchGridCellList(); // Fetch initial state

  document.addEventListener('keydown', handleGlobalKeyDown);
  document.addEventListener('keyup', handleGlobalKeyUp);

  // Set up interval to fetch grid cell list
  const urlParams = new URLSearchParams(window.location.search);
  const interval = parseInt(urlParams.get('interval'), 10) || config.timing.defaultPollingInterval;
  gridCellListInterval = setInterval(fetchGridCellList, interval);

  // Add window resize event listener to adjust grid when window size changes
  window.addEventListener('resize', () => {
    // Use debounce to avoid excessive recalculations during resize
    clearTimeout(window.resizeTimer);
    window.resizeTimer = createTimeout(() => {
      console.info(`${new Date().toISOString()} `, 'Window resized, recalculating grid dimensions...');
      createGrid();
      // Recreate axes after grid is resized
      createTimeout(() => {
        createLeftAxis();
        createBottomAxis();
      }, 0);
    }, config.timing.windowResizeDebounce);
  });

  // Store agent step messages in a FIFO queue with a maximum size
  const agentStepMessages = [];
  const agentStepMessagesMax = config.agent.messageQueueMax;
  let agentStepEventSource = null; // EventSource instance

  // Connect to agent step stream for the current session
  connectToAgentStepStream();

  /**
   * Connects to the agent step stream to receive real-time updates about voice command processing
   */
  function connectToAgentStepStream() {
    if (agentStepEventSource && agentStepEventSource.readyState !== EventSource.CLOSED) {
      console.info(`${new Date().toISOString()} `, 'Agent step SSE stream already open or connecting.');
      return;
    }

    const sessionId = getSessionId();
    const url = `${origin}${config.endpoints.agentStepsStream}/${sessionId}`;
    console.info(`${new Date().toISOString()} `, `Agent step SSE Attempting to connect SSE to ${url}...`);
    updateConnectionStatus('Connecting...', '');
    agentStepEventSource = new EventSource(url);

    const readyStateMap = {
      0: '(0) Connecting',
      1: '(1) Open',
      2: '(2) Closed',
    };

    agentStepEventSource.onopen = (event) => {
      console.info(`${new Date().toISOString()} `, `Agent step SSE connection established, readyState: ${readyStateMap[agentStepEventSource.readyState]}.`);
      updateConnectionStatus('Connected', 'connected');
    };

    agentStepEventSource.onmessage = (event) => {
      // Skip empty messages (keep-alive polling)
      if (!event.data || event.data.trim() === '') {
        return;
      }

      try {
        const step = JSON.parse(event.data);
        // Add the message to our FIFO queue
        addAgentStepMessage(step);

        // Process the step based on its status and content
        if (step.status === 'processed' && step.llmResponse) {
          // This is a processed step with LLM response
          console.info(`${new Date().toISOString()} `, 'Agent step processed step with LLM response:', step.llmResponse);
        }

        // Update UI to show the current step being processed
        updateCommandStatus(`Processing: ${step.userPrompt || step.llmPrompt || 'Voice command'}`, 5000);
      } catch (error) {
        console.error(`${new Date().toISOString()} `, 'Agent step Error processing agent step:', error, 'Raw data:', event.data);
      }
    };

    agentStepEventSource.onerror = (event) => {
      console.error(`${new Date().toISOString()} `, `Agent step SSE error, EventSource readyState: ${readyStateMap[agentStepEventSource.readyState]}`);
      if (agentStepEventSource.readyState === EventSource.CONNECTING) {
        return;
      }
      console.error(`${new Date().toISOString()} `, 'Agent step SSE error:', event);
      updateConnectionStatus('Error', 'error');

      if (agentStepEventSource.readyState === EventSource.CLOSED) {
        console.info(`${new Date().toISOString()} `, 'Agent step SSE connection closed.');
        updateConnectionStatus('Disconnected', 'error');
        agentStepEventSource = null; // Clear the instance

        // Optional: Attempt to reconnect after a delay
        console.info(`${new Date().toISOString()} `, 'Agent step SSE Attempting to reconnect...');
        createTimeout(connectToAgentStepStream, config.timing.reconnectDelay);
      }
    };
  }

  /**
   * Adds a message to the agent step message queue, maintaining the maximum size
   * @param {Object} message - The agent step message to add
   */
  function addAgentStepMessage(message) {
    if (!message.timestamp) {
      message.timestamp = new Date().toISOString();
    }
    const existingMessage = agentStepMessages.find((m) => m.id === message.id && m.status === message.status);
    if (existingMessage) {
      console.debug(`${new Date().toISOString()} `, `Agent step message with id ${message.id} already exists, skipping`);
      return;
    }

    console.info(`${new Date().toISOString()} `, 'Agent step update:', message);

    agentStepMessages.push(message);

    if (agentStepMessages.length > agentStepMessagesMax) {
      agentStepMessages.shift();
    }

    const putAgentStepConsumed = async (message) => {
      console.info(`${new Date().toISOString()} `, 'Consuming agent step:', message);
      try {
        await apiCall(config.endpoints.agentStepConsumed, {
          method: 'PUT',
          body: JSON.stringify({
            id: message.id,
            sequenceId: message.sequenceId,
            stepNumber: message.stepNumber,
          }),
        });
      } catch (error) {
        // Error already logged by apiCall
      }
    };
    putAgentStepConsumed(message);

    console.debug(`${new Date().toISOString()} `, `Agent step message queue size: ${agentStepMessages.length}`);
  }

  /**
   * Gets the current list of agent step messages
   * @returns {Array} The current list of agent step messages
   */
  function getAgentStepMessages() {
    return [...agentStepMessages]; // Return a copy to prevent external modification
  }

  // Track the current index in the agent message list for navigation
  let currentAgentMessageIndex = -1;

  /**
   * Toggles the agent message overlay visibility
   */
  function toggleAgentMessageOverlay() {
    const existingOverlay = document.querySelector('.agent-message-overlay');
    if (existingOverlay) {
      removeAgentMessageOverlay();
    } else {
      showAgentMessageOverlay();
    }
  }

  /**
   * Removes the agent message overlay
   */
  function removeAgentMessageOverlay() {
    const overlay = document.querySelector('.agent-message-overlay');
    if (overlay) {
      document.body.removeChild(overlay);
      currentAgentMessageIndex = -1;
    }
  }

  /**
   * Shows the agent message overlay with the latest message
   */
  function showAgentMessageOverlay() {
    removeAgentMessageOverlay();

    const messages = getAgentStepMessages();
    if (messages.length === 0) {
      updateCommandStatus('No agent messages available', 3000);
      return;
    }

    // Start with the most recent message
    currentAgentMessageIndex = messages.length - 1;
    displayAgentMessage(currentAgentMessageIndex);
  }

  /**
   * Navigates through agent messages
   * @param {number} direction - Direction to navigate: -1 for previous, 1 for next
   */
  function navigateAgentMessages(direction) {
    const messages = getAgentStepMessages();
    if (messages.length === 0) return;

    // Calculate new index with wrap-around
    const newIndex = (currentAgentMessageIndex + direction + messages.length) % messages.length;
    currentAgentMessageIndex = newIndex;

    // Update the display
    displayAgentMessage(currentAgentMessageIndex);
  }

  /**
   * Displays a specific agent message in the overlay
   * @param {number} index - Index of the message to display
   */
  function displayAgentMessage(index) {
    const messages = getAgentStepMessages();
    if (index < 0 || index >= messages.length) return;

    const message = messages[index];

    // Create or update overlay
    let overlay = document.querySelector('.agent-message-overlay');
    if (!overlay) {
      overlay = createOverlay('agent-message-overlay', {
        styles: {
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'flex-start',
          alignItems: 'center',
          fontSize: '12px',
          border: '2px solid #be43a4',
          borderRadius: '7px',
          boxShadow: '0 0 16px #be43a4',
          padding: '14px 18px',
          maxWidth: '500px',
          maxHeight: '80vh',
          overflowY: 'auto',
          right: '20px',
          top: '20px',
        },
      });
    } else {
      // Clear existing content
      overlay.innerHTML = '';
    }

    // Add header with navigation info
    const header = document.createElement('div');
    header.style.width = '100%';
    header.style.marginBottom = '10px';
    header.style.display = 'flex';
    header.style.justifyContent = 'space-between';
    header.style.alignItems = 'center';

    const title = document.createElement('div');
    title.textContent = 'Agent Step';
    title.style.fontWeight = 'bold';
    title.style.fontSize = '1.2em';
    title.style.color = '#6fffc8';

    const navigation = document.createElement('div');
    navigation.textContent = `${index + 1}/${messages.length}`;
    navigation.style.color = '#fff';

    header.appendChild(title);
    header.appendChild(navigation);
    overlay.appendChild(header);

    // Add navigation instructions
    const instructions = document.createElement('div');
    instructions.textContent = 'Use ↑/↓ arrows to navigate, ESC or A to close';
    instructions.style.fontSize = '0.9em';
    instructions.style.color = '#999';
    instructions.style.marginBottom = '10px';
    instructions.style.width = '100%';
    instructions.style.textAlign = 'center';
    overlay.appendChild(instructions);

    // Add timestamp if available
    if (message.timestamp) {
      const timestamp = document.createElement('div');
      timestamp.textContent = new Date(message.timestamp).toLocaleString();
      timestamp.style.fontSize = '0.9em';
      timestamp.style.color = '#999';
      timestamp.style.marginBottom = '10px';
      timestamp.style.width = '100%';
      timestamp.style.textAlign = 'right';
      overlay.appendChild(timestamp);
    }

    // Add message content as formatted JSON
    const content = document.createElement('pre');
    content.style.width = '100%';
    content.style.overflow = 'auto';
    content.style.fontFamily = 'monospace';
    content.style.fontSize = '1.2em';
    content.style.color = '#fff';
    content.style.backgroundColor = 'rgba(0,0,0,0.2)';
    content.style.padding = '10px';
    content.style.borderRadius = '4px';
    content.style.margin = '0';
    content.style.whiteSpace = 'pre-wrap'; /* Preserve formatting but allow wrapping */
    content.style.overflowWrap = 'break-word'; /* Break long words if needed */

    // Format JSON nicely with 2-space indentation
    try {
      // Create a deep copy of the message to modify
      const formattedMessage = JSON.parse(JSON.stringify(message));

      // Check if llmResponse exists and might contain JSON
      if (formattedMessage.llmResponse && typeof formattedMessage.llmResponse === 'string') {
        try {
          // Try to parse the llmResponse as JSON
          const parsedResponse = JSON.parse(formattedMessage.llmResponse);
          // If successful, replace the string with the parsed object
          formattedMessage.llmResponse = parsedResponse;
        } catch (jsonError) {
          // Not valid JSON, keep as string
          console.debug('llmResponse is not valid JSON:', jsonError);
        }
      }

      content.textContent = JSON.stringify(formattedMessage, null, 2);
    } catch (e) {
      content.textContent = 'Error formatting message: ' + e.message;
    }

    overlay.appendChild(content);
  }
}); // End DOMContentLoaded
