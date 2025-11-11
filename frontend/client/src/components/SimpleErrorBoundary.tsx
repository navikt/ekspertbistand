import { Component, type ErrorInfo, type ReactNode } from "react";

type SimpleErrorBoundaryProps = {
  fallback: ReactNode;
  children: ReactNode;
};

type SimpleErrorBoundaryState = {
  hasError: boolean;
};

export class SimpleErrorBoundary extends Component<
  SimpleErrorBoundaryProps,
  SimpleErrorBoundaryState
> {
  state: SimpleErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error("Unhandled error caught by SimpleErrorBoundary", error, errorInfo);
    }
  }

  render() {
    if (this.state.hasError) {
      return this.props.fallback;
    }
    return this.props.children;
  }
}
