import type { CSSProperties, MouseEventHandler, ReactNode } from "react";
import { Link as RouterLink, type To, type NavigateOptions } from "react-router-dom";
import { ArrowLeftIcon } from "@navikt/aksel-icons";
import { Link } from "@navikt/ds-react";

const BASE_STYLE: CSSProperties = {
  display: "inline-flex",
  alignItems: "center",
  gap: "0.5rem",
};

type CommonProps = {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
  onClick?: MouseEventHandler<HTMLAnchorElement>;
  icon?: ReactNode;
};

type InternalBackLinkProps = CommonProps & {
  to: To;
  state?: NavigateOptions["state"];
  replace?: NavigateOptions["replace"];
};

type ExternalBackLinkProps = CommonProps & {
  href: string;
  target?: string;
  rel?: string;
};

export type BackLinkProps = InternalBackLinkProps | ExternalBackLinkProps;

const defaultIcon = <ArrowLeftIcon aria-hidden focusable="false" />;

export function BackLink(props: BackLinkProps) {
  const { children, className, style, onClick, icon } = props;
  const linkStyle = style ? { ...BASE_STYLE, ...style } : BASE_STYLE;
  const content = (
    <>
      {icon ?? defaultIcon}
      <span>{children}</span>
    </>
  );

  if ("to" in props) {
    const { to, state, replace } = props;
    return (
      <Link
        as={RouterLink}
        to={to}
        state={state}
        replace={replace}
        className={className}
        onClick={onClick}
        style={linkStyle}
      >
        {content}
      </Link>
    );
  }

  const { href, target, rel } = props;
  return (
    <Link
      href={href}
      target={target}
      rel={rel}
      className={className}
      onClick={onClick}
      style={linkStyle}
    >
      {content}
    </Link>
  );
}
