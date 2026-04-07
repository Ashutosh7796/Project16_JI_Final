// Custom Swagger UI Bundle Configuration
// This file enhances the Swagger UI with custom styling and functionality

(function() {
    'use strict';
    
    // Wait for Swagger UI to load
    function waitForSwaggerUI() {
        if (typeof SwaggerUIBundle !== 'undefined') {
            initializeCustomSwaggerUI();
        } else {
            setTimeout(waitForSwaggerUI, 100);
        }
    }
    
    function initializeCustomSwaggerUI() {
        // Inject custom CSS
        const customCSS = document.createElement('link');
        customCSS.rel = 'stylesheet';
        customCSS.type = 'text/css';
        customCSS.href = '/swagger-ui-custom.css';
        document.head.appendChild(customCSS);
        
        // Add custom JavaScript functionality
        setTimeout(function() {
            addCustomFunctionality();
        }, 1000);
    }
    
    function addCustomFunctionality() {
        // Add admin badge to admin endpoints
        const adminEndpoints = document.querySelectorAll('[data-tag*="Admin"]');
        adminEndpoints.forEach(function(endpoint) {
            if (!endpoint.querySelector('.admin-badge')) {
                const badge = document.createElement('span');
                badge.className = 'admin-badge';
                badge.innerHTML = '🔐 ADMIN';
                badge.style.cssText = `
                    background: #dc3545;
                    color: white;
                    padding: 2px 8px;
                    border-radius: 12px;
                    font-size: 10px;
                    font-weight: bold;
                    margin-left: 10px;
                    display: inline-block;
                `;
                
                const summary = endpoint.querySelector('.opblock-summary-description');
                if (summary) {
                    summary.appendChild(badge);
                }
            }
        });
        
        // Add group indicators
        addGroupIndicators();
        
        // Add custom header
        addCustomHeader();
    }
    
    function addGroupIndicators() {
        const groups = {
            'Admin': { color: '#dc3545', icon: '🔐' },
            'User': { color: '#007bff', icon: '🧑‍💼' },
            'Public': { color: '#28a745', icon: '🌐' }
        };
        
        Object.keys(groups).forEach(function(groupName) {
            const elements = document.querySelectorAll(`[data-tag*="${groupName}"]`);
            elements.forEach(function(element) {
                if (!element.querySelector('.group-indicator')) {
                    const indicator = document.createElement('span');
                    indicator.className = 'group-indicator';
                    indicator.innerHTML = groups[groupName].icon;
                    indicator.style.cssText = `
                        color: ${groups[groupName].color};
                        font-size: 16px;
                        margin-right: 8px;
                        display: inline-block;
                    `;
                    
                    const summary = element.querySelector('.opblock-summary');
                    if (summary) {
                        summary.insertBefore(indicator, summary.firstChild);
                    }
                }
            });
        });
    }
    
    function addCustomHeader() {
        const infoSection = document.querySelector('.swagger-ui .info');
        if (infoSection && !document.querySelector('.custom-api-header')) {
            const header = document.createElement('div');
            header.className = 'custom-api-header';
            header.innerHTML = `
                <div style="
                    background: linear-gradient(135deg, #dc3545, #fd7e14);
                    color: white;
                    padding: 20px;
                    border-radius: 12px;
                    margin: 20px 0;
                    text-align: center;
                    box-shadow: 0 4px 12px rgba(220, 53, 69, 0.2);
                ">
                    <h2 style="margin: 0; font-size: 1.8rem; font-weight: bold;">
                        🚀 BitFlirt API Documentation
                    </h2>
                    <p style="margin: 10px 0 0 0; opacity: 0.9; font-size: 1.1rem;">
                        Complete API documentation with separate Admin and User endpoints
                    </p>
                    <div style="margin-top: 15px; display: flex; justify-content: center; gap: 20px; flex-wrap: wrap;">
                        <span style="background: rgba(255,255,255,0.2); padding: 5px 12px; border-radius: 20px; font-size: 0.9rem;">
                            🔐 Admin APIs: Administrative operations
                        </span>
                        <span style="background: rgba(255,255,255,0.2); padding: 5px 12px; border-radius: 20px; font-size: 0.9rem;">
                            🧑‍💼 User APIs: Regular user operations
                        </span>
                        <span style="background: rgba(255,255,255,0.2); padding: 5px 12px; border-radius: 20px; font-size: 0.9rem;">
                            🌐 Public APIs: No authentication required
                        </span>
                    </div>
                </div>
            `;
            
            infoSection.insertBefore(header, infoSection.firstChild);
        }
    }
    
    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', waitForSwaggerUI);
    } else {
        waitForSwaggerUI();
    }
})();