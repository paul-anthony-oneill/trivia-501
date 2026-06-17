"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { adminApi } from "@/lib/api/admin";
import type { BulkActivateResult, Category, Question, QuestionStatus } from "@/lib/types/admin";
import DataTable from "@/components/admin/DataTable";
import Select from "@/components/ui/Select";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import { useToast } from "@/context/ToastContext";

type QuestionRow = Question & { difficultyLabel: string; statusLabel: string };

const statusBadgeClass: Record<QuestionStatus, string> = {
  draft:    "bg-[rgba(156,163,175,0.15)] text-[var(--color-on-surface-variant)] border-[var(--color-on-surface-variant)]",
  active:   "bg-[var(--color-primary-container)] text-[var(--color-primary)] border-[var(--color-primary)]",
  retired:  "bg-[var(--color-error-container)] text-[var(--color-error)] border-[var(--color-error)]",
  excluded: "bg-[var(--color-excluded-container)] text-[var(--color-excluded)] border-[var(--color-excluded)]",
};

/** Next status to transition to and the button label, given current status. */
function statusAction(status: QuestionStatus): { label: string; next: QuestionStatus; color: string } | null {
  switch (status) {
    case "draft":    return { label: "Activate", next: "active",  color: "var(--color-primary)" };
    case "active":   return { label: "Retire",   next: "retired", color: "var(--color-retire)" };
    case "retired":  return { label: "Restore",  next: "active",  color: "var(--color-restore)" };
    case "excluded": return null;
  }
}

const columns = [
  { key: "questionText",    label: "Question" },
  { key: "categoryName",    label: "Category" },
  { key: "difficultyLabel", label: "Difficulty" },
  { key: "answerCount",     label: "Answers" },
  { key: "statusLabel",     label: "Status" },
  { key: "suitableForDaily", label: "Daily" },
];

const statusOptions = [
  { value: "",         label: "All Status" },
  { value: "draft",    label: "Draft" },
  { value: "active",   label: "Active" },
  { value: "retired",  label: "Retired" },
  { value: "excluded", label: "Excluded" },
];

function difficultyLabel(d: number) {
  return d === 1 ? "Easy" : d === 3 ? "Hard" : "Medium";
}

function statusLabel(s: QuestionStatus) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export default function QuestionsPage() {
  const { addToast } = useToast();
  const router = useRouter();

  const [questions, setQuestions] = useState<QuestionRow[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalElements, setTotalElements] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const pageSize = 10;

  // Tracks which question ID has an in-flight mutation (status or daily toggle).
  const [pendingId, setPendingId] = useState<string | null>(null);

  // Filters (held in state; applied only on "Apply Filters")
  const [selectedCategory, setSelectedCategory] = useState("");
  const [selectedStatus, setSelectedStatus] = useState("");

  // Applied filters (the ones actually sent to the API)
  const [appliedCategory, setAppliedCategory] = useState("");
  const [appliedStatus, setAppliedStatus] = useState("");

  // Delete dialog
  const [showDelete, setShowDelete]             = useState(false);
  const [selectedQuestion, setSelectedQuestion] = useState<Question | null>(null);

  // Bulk activate
  const [bulkLimit, setBulkLimit]           = useState(200);
  const [bulkActivating, setBulkActivating] = useState(false);
  const [lastBulkResult, setLastBulkResult] = useState<BulkActivateResult | null>(null);

  const loadQuestions = useCallback(
    async (page: number, catId: string, status: string) => {
      setLoading(true);
      try {
        const res = await adminApi.listQuestions(
          catId  || undefined,
          status || undefined,
          page,
          pageSize
        );
        setQuestions(
          res.content.map((q) => ({
            ...q,
            difficultyLabel: difficultyLabel(q.difficulty),
            statusLabel: statusLabel(q.status),
          }))
        );
        setTotalElements(res.totalElements);
      } catch (err) {
        addToast((err as Error).message, "error");
      } finally {
        setLoading(false);
      }
    },
    [addToast]
  );

  useEffect(() => {
    adminApi.listCategories().then(setCategories).catch((err) => {
      addToast((err as Error).message, "error");
    });
  }, [addToast]);

  useEffect(() => {
    loadQuestions(0, "", "");
  }, [loadQuestions]);

  function applyFilters() {
    setAppliedCategory(selectedCategory);
    setAppliedStatus(selectedStatus);
    setCurrentPage(0);
    loadQuestions(0, selectedCategory, selectedStatus);
  }

  function handlePageChange(page: number) {
    setCurrentPage(page);
    loadQuestions(page, appliedCategory, appliedStatus);
  }

  async function handleStatusToggle(question: QuestionRow) {
    const action = statusAction(question.status);
    if (!action || pendingId) return;
    setPendingId(question.id);
    try {
      await adminApi.updateQuestionStatus(question.id, { status: action.next });
      addToast(`Question ${action.label.toLowerCase()}d`, "success");
      loadQuestions(currentPage, appliedCategory, appliedStatus);
    } catch (err) {
      addToast((err as Error).message, "error");
    } finally {
      setPendingId(null);
    }
  }

  async function handleDailyToggle(question: QuestionRow) {
    if (pendingId) return;
    setPendingId(question.id);
    try {
      await adminApi.updateSuitableForDaily(question.id, !question.suitableForDaily);
      addToast(
        question.suitableForDaily ? "Removed from daily pool" : "Added to daily pool",
        "success"
      );
      loadQuestions(currentPage, appliedCategory, appliedStatus);
    } catch (err) {
      addToast((err as Error).message, "error");
    } finally {
      setPendingId(null);
    }
  }

  async function handleBulkActivate() {
    setBulkActivating(true);
    try {
      const result = await adminApi.bulkActivateQuestions(bulkLimit);
      setLastBulkResult(result);
      addToast(
        `Activated ${result.activated} questions · ${result.answersUpserted.toLocaleString()} answers · ${result.remainingDraft.toLocaleString()} draft remaining`,
        result.errors > 0 ? "info" : "success"
      );
      loadQuestions(currentPage, appliedCategory, appliedStatus);
    } catch (err) {
      addToast((err as Error).message, "error");
    } finally {
      setBulkActivating(false);
    }
  }

  async function handleDelete() {
    if (!selectedQuestion) return;
    try {
      await adminApi.deleteQuestion(selectedQuestion.id);
      addToast("Question deleted successfully", "success");
      setShowDelete(false);
      loadQuestions(currentPage, appliedCategory, appliedStatus);
    } catch (err) {
      addToast((err as Error).message, "error");
    }
  }

  const categoryOptions = [
    { value: "", label: "All Categories" },
    ...categories.map((c) => ({ value: c.id, label: c.name })),
  ];

  return (
    <div>
      {/* Header */}
      <div className="flex justify-between items-center mb-8">
        <h1 className="m-0 text-[var(--color-primary)]">Questions</h1>
        <Link
          href="/admin/questions/create"
          className="bg-[var(--color-primary)] text-black px-6 py-3 rounded-lg font-semibold no-underline hover:opacity-80 transition-opacity"
        >
          + New Question
        </Link>
      </div>

      {/* Bulk Activate panel */}
      <div className="mb-8 p-5 rounded-xl bg-[var(--color-surface-variant)] border border-[var(--color-outline)]">
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h3 className="m-0 text-sm font-semibold text-[var(--color-on-surface)]">
              Bulk Activate Draft Questions
            </h3>
            <p className="m-0 mt-1 text-xs text-[var(--color-on-surface-variant)]">
              Promotes draft → active and materialises answers from{" "}
              <code className="bg-[var(--color-input-bg)] px-1 rounded">player_season_stints</code>.
              Run repeatedly until all questions are active.
              {lastBulkResult && (
                <span className="ml-2 text-[var(--color-primary)]">
                  Last run: {lastBulkResult.activated} activated ·{" "}
                  {lastBulkResult.answersUpserted.toLocaleString()} answers ·{" "}
                  {lastBulkResult.remainingDraft.toLocaleString()} remaining
                  {lastBulkResult.errors > 0 && (
                    <span className="text-[var(--color-excluded)]"> · {lastBulkResult.errors} errors</span>
                  )}
                </span>
              )}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 text-sm text-[var(--color-on-surface-variant)]">
              <label htmlFor="bulk-limit">Batch size:</label>
              <input
                id="bulk-limit"
                type="number"
                min={1}
                max={500}
                value={bulkLimit}
                onChange={(e) => setBulkLimit(Math.min(500, Math.max(1, Number(e.target.value))))}
                className="w-20 px-2 py-1 rounded bg-[var(--color-input-bg)] border border-[var(--color-input-border)] text-white text-sm text-center"
              />
            </div>
            <button
              onClick={handleBulkActivate}
              disabled={bulkActivating}
              className="px-5 py-2 rounded-lg bg-[var(--color-primary)] text-black text-sm font-semibold cursor-pointer hover:opacity-90 transition-opacity disabled:opacity-50 border-0 whitespace-nowrap"
            >
              {bulkActivating ? "Activating…" : "⚡ Bulk Activate"}
            </button>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-4 mb-6 bg-[var(--color-surface-variant)] p-4 rounded-lg items-end">
        <div className="flex-1 max-w-[200px]">
          <Select
            label="Category"
            value={selectedCategory}
            onChange={setSelectedCategory}
            options={categoryOptions}
          />
        </div>
        <div className="flex-1 max-w-[200px]">
          <Select
            label="Status"
            value={selectedStatus}
            onChange={setSelectedStatus}
            options={statusOptions}
          />
        </div>
        <div className="mb-4">
          <button
            onClick={applyFilters}
            className="bg-[var(--color-filter-btn)] text-white px-6 py-3 rounded-lg border-none cursor-pointer font-medium hover:opacity-90 transition-opacity"
          >
            Apply Filters
          </button>
        </div>
      </div>

      {/* Status legend */}
      <div className="flex gap-3 mb-4 flex-wrap items-center">
        {(["draft", "active", "retired", "excluded"] as QuestionStatus[]).map((s) => (
          <span
            key={s}
            className={`px-2 py-0.5 rounded text-[0.75rem] font-medium border ${statusBadgeClass[s]}`}
          >
            {statusLabel(s)}
          </span>
        ))}
        <span className="text-[var(--color-on-surface-variant)] text-[0.75rem]">
          · Daily column: whether question is in the daily challenge pool
        </span>
      </div>

      <DataTable
        columns={columns}
        data={questions}
        loading={loading}
        totalElements={totalElements}
        pageSize={pageSize}
        currentPage={currentPage}
        onPageChange={handlePageChange}
        renderCell={(key, item) => {
          if (key === "statusLabel") {
            return (
              <span
                className={`px-2 py-0.5 rounded text-[0.75rem] font-medium border ${statusBadgeClass[item.status]}`}
              >
                {item.statusLabel}
              </span>
            );
          }
          if (key === "suitableForDaily") {
            return (
              <button
                role="switch"
                aria-checked={item.suitableForDaily}
                onClick={() => handleDailyToggle(item)}
                disabled={pendingId === item.id}
                title={item.suitableForDaily ? "Remove from daily pool" : "Add to daily pool"}
                className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors focus:outline-none disabled:opacity-40 ${
                  item.suitableForDaily ? "bg-[var(--color-primary)]" : "bg-[var(--color-toggle-off)]"
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                    item.suitableForDaily ? "translate-x-4" : "translate-x-0"
                  }`}
                />
              </button>
            );
          }
          return undefined;
        }}
        renderActions={(item) => {
          const action = statusAction(item.status);
          const isPending = pendingId === item.id;
          return (
            <>
              {action && (
                <button
                  onClick={() => handleStatusToggle(item)}
                  disabled={isPending || !!pendingId}
                  title={`${action.label} this question`}
                  style={{ color: action.color, borderColor: action.color }}
                  className="px-2 py-1 text-[0.7rem] font-semibold rounded border bg-transparent cursor-pointer hover:opacity-80 transition-opacity disabled:opacity-40 whitespace-nowrap"
                >
                  {isPending ? "…" : action.label}
                </button>
              )}
              <button
                onClick={() => router.push(`/admin/questions/${item.id}`)}
                title="Edit answers & details"
                className="w-10 h-10 flex items-center justify-center bg-[var(--color-surface-variant)] rounded-[var(--radius-sm)] hover:bg-[var(--color-primary-container)] transition-colors"
              >
                ✏️
              </button>
              <button
                onClick={() => { setSelectedQuestion(item); setShowDelete(true); }}
                title="Delete"
                className="w-10 h-10 flex items-center justify-center bg-[var(--color-surface-variant)] rounded-[var(--radius-sm)] hover:bg-[var(--color-error-container)] transition-colors"
              >
                🗑️
              </button>
            </>
          );
        }}
      />

      <ConfirmDialog
        open={showDelete}
        title="Delete Question"
        message={`Are you sure you want to delete this question? All ${selectedQuestion?.answerCount ?? 0} answers will also be deleted.`}
        onConfirm={handleDelete}
        onCancel={() => setShowDelete(false)}
      />
    </div>
  );
}
