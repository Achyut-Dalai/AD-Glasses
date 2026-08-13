(function() {
    'use strict';

    // The real EvenAppBridge will try to inject itself from the Flutter host.
    // We intercept it by defining our own first.
    class EvenAppBridgeShim {
        constructor() {
            this._listeners = [];
            this._ready = true;
        }

        static getInstance() {
            if (!window._evenAppBridgeInstance) {
                window._evenAppBridgeInstance = new EvenAppBridgeShim();
            }
            return window._evenAppBridgeInstance;
        }

        async createStartUpPageContainer(params) {
            console.log('[EvenHub Shim] createStartUpPageContainer', params);
            const text = this._extractText(params);
            if (text && window.ADGlassesEvenHubBridge) {
                window.ADGlassesEvenHubBridge.createPage(JSON.stringify(params));
            }
            return 0; // success
        }

        async textContainerUpgrade(containerID, containerName, content, contentOffset, contentLength) {
            console.log('[EvenHub Shim] textContainerUpgrade', containerID, containerName, content);
            if (window.ADGlassesEvenHubBridge) {
                window.ADGlassesEvenHubBridge.updateText(containerID, containerName, content, contentOffset, contentLength);
            }
            return true;
        }

        async rebuildPageContainer(params) {
            console.log('[EvenHub Shim] rebuildPageContainer', params);
            if (window.ADGlassesEvenHubBridge) {
                window.ADGlassesEvenHubBridge.rebuildPage(JSON.stringify(params));
            }
            return true;
        }

        async updateImageRawData(params) {
            console.log('[EvenHub Shim] updateImageRawData (not yet supported)');
            return 'success';
        }

        async shutDownPageContainer(exitMode) {
            console.log('[EvenHub Shim] shutDownPageContainer', exitMode);
            if (window.ADGlassesEvenHubBridge) {
                window.ADGlassesEvenHubBridge.shutDown(exitMode);
            }
            return true;
        }

        onEvenHubEvent(listener) {
            this._listeners.push(listener);
            console.log('[EvenHub Shim] Event listener registered');
            return () => {
                const idx = this._listeners.indexOf(listener);
                if (idx >= 0) this._listeners.splice(idx, 1);
            };
        }

        audioControl(start) {
            console.log('[EvenHub Shim] audioControl', start);
            return Promise.resolve();
        }

        imuControl(enable, pace) {
            console.log('[EvenHub Shim] imuControl', enable, pace);
            return Promise.resolve();
        }

        getDeviceInfo() {
            return Promise.resolve({
                model: 'MemoMind (via AD Glasses)',
                serialNumber: 'N/A',
                battery: 85,
                wearingStatus: 'unknown',
                charging: false,
                inCase: false,
            });
        }

        getUserInfo() {
            return Promise.resolve({
                uid: 'adglasses-user',
                name: 'AD Glasses User',
                avatar: '',
                country: 'US',
            });
        }

        setLocalStorage(key, value) {
            try { localStorage.setItem('evenhub_' + key, value); } catch(e) {}
        }

        getLocalStorage(key) {
            try { return localStorage.getItem('evenhub_' + key); } catch(e) { return null; }
        }

        callEvenApp(method, params) {
            console.log('[EvenHub Shim] callEvenApp', method, params);
            return this[method] ? this[method](params) : Promise.resolve(null);
        }

        // Dispatch an event to all registered listeners (called from Android)
        _dispatchEvent(event) {
            for (const listener of this._listeners) {
                try { listener(event); } catch(e) { console.error('[EvenHub Shim] Event error', e); }
            }
        }

        _extractText(params) {
            if (params && params.textObject && params.textObject.length > 0) {
                return params.textObject[0].content || '';
            }
            return '';
        }
    }

    // Inject the shim immediately
    const shim = EvenAppBridgeShim.getInstance();
    window.EvenAppBridge = { getInstance: () => shim };
    window._evenAppBridgeInstance = shim;

    // Also provide the async waitForEvenAppBridge
    window.waitForEvenAppBridge = () => Promise.resolve(shim);

    // If the SDK's own bridge tries to overwrite, let it (but our shim was first)
    console.log('[EvenHub Compat] Shim installed');
})();
