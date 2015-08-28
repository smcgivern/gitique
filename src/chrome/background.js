chrome.tabs.onUpdated.addListener(function(tabId, changeInfo) {
  if (changeInfo.status !== 'complete') { return; }

  chrome.tabs.executeScript(tabId, {code: 'window.gitiqueInjected;'}, function(res) {
    if (res && !res[0]) {
      chrome.tabs.executeScript(tabId, {code: 'window.gitiqueInjected = true;'});
      chrome.tabs.executeScript(tabId, {file: 'gitique.js'}, function() {
        chrome.tabs.executeScript(tabId, {code: 'gitique.core.watch();'});
      });
      chrome.tabs.insertCSS(tabId, {file: 'gitique.css'});
    }
  });
});
