(function () {
    "use strict";
    var dialog = document.querySelector("[data-created-dialog]");
    if (!dialog || typeof dialog.showModal !== "function") return;
    dialog.showModal();
    var close = dialog.querySelector("[data-close-dialog]");
    if (close) close.addEventListener("click", function () { dialog.close(); });
}());
