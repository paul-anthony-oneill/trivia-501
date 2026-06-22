"use client";

import { useEffect, useRef, useCallback } from "react";

interface ConfirmDialogProps {
  open: boolean;
  title?: string;
  message?: string;
  confirmText?: string;
  cancelText?: string;
  type?: "danger" | "info";
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({
  open,
  title = "Confirm",
  message = "Are you sure you want to proceed?",
  confirmText = "Confirm",
  cancelText = "Cancel",
  type = "danger",
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  const handleCancel = useCallback(() => {
    onCancel();
  }, [onCancel]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      onClose={handleCancel}
      className="bg-surface border border-line text-ink p-7 rounded-md w-[90%] max-w-[400px] shadow-[var(--shadow-pop)] backdrop:bg-black/60 backdrop:backdrop-blur-sm"
    >
      <h3 id="confirm-title" className="m-0 mb-3 font-display font-bold text-xl tracking-tight">{title}</h3>
      <p id="confirm-message" className="text-muted text-sm mb-7 leading-relaxed">{message}</p>
      <div className="flex justify-end gap-3">
        <button
          className="px-5 py-2.5 rounded-sm border border-line text-muted font-medium text-sm hover:text-ink hover:border-line-strong transition-colors"
          onClick={onCancel}
        >
          {cancelText}
        </button>
        <button
          className={`px-5 py-2.5 rounded-sm font-medium text-sm transition-opacity ${type === "danger" ? "bg-danger text-white hover:opacity-90" : "bg-ink text-bg hover:opacity-90"}`}
          onClick={onConfirm}
        >
          {confirmText}
        </button>
      </div>
    </dialog>
  );
}
