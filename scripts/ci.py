#!/usr/bin/env python3
#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0

import argparse
import os
import subprocess
import shlex

GIT_ORIGIN_REFIX = "refs/heads/"

def running_in_github_action():
    """
    Test if currently running in a GitHub action or running locally
    :return: True if running in GH, False otherwise
    """
    return "GITHUB_WORKFLOW" in os.environ


def shell(command, cwd=None, check=True):
    """
    Run a command
    :param command: command to run
    :param shell: Flag indicating if shell should be used by subprocess command
    """
    if not shell:
        command = shlex.split(command)

    print(f"running `{command}`")
    return subprocess.run(command, shell=True, check=check, cwd=cwd, capture_output=True)


def get_current_branch(repo_dir):
    """
    Get the current branch name for the repository rooted at repo_dir
    """
    pout = shell("git branch --show-current", cwd=repo_dir)
    return pout.stdout.decode("utf-8").strip()


def has_remote_branch(repo_dir, branch_name):
    """
    Check if the repository rooted at repo_dir has the given branch name. Returns true if it does, false otherwise.
    """
    pout = shell(f"git ls-remote --exit-code origin {GIT_ORIGIN_REFIX}{branch_name}", cwd=repo_dir, check=False)
    return pout.returncode == 0


def update_repo(repo_dir, branch_name):
    """
    Attempt to update the repository to the given branch name
    :param repo_dir:
    :param branch_name:
    :return:
    """
    branch_name = branch_name.remove_prefix(GIT_ORIGIN_REFIX)
    curr_branch = get_current_branch(repo_dir)
    print(f"current branch of `{repo_dir}`: {curr_branch}")
    if curr_branch == branch_name:
        print("branches match already, nothing to do")
        return

    has_branch = has_remote_branch(repo_dir, branch_name)
    if has_branch:
        print(f"repo has target branch `{branch_name}`: {has_branch}...updating")
        pout = shell(f"git switch {branch_name}", cwd=repo_dir)
        print(pout.stdout.decode("utf-8"))
    else:
        print(f"repo does not have target branch `{branch_name}`, leaving at {curr_branch}")


def _get_branch_cmd(opts):
    branch = "main"
    if running_in_github_action():
        gh_head_ref = os.environ.get("GITHUB_HEAD_REF")
        gh_ref = os.environ.get("GITHUB_REF")
        if gh_head_ref:
            branch = gh_head_ref
        elif gh_ref:
            branch = gh_ref.removeprefix(GIT_ORIGIN_REFIX)
    else:
        branch = get_current_branch(opts.repository)

    print(branch)


def _set_branch_cmd(opts):
    update_repo(opts.repository, opts.branch)


def create_cli():
    parser = argparse.ArgumentParser(
        prog="ci",
        description="Utilities for setting up dependencies correctly in CI",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )

    subparsers = parser.add_subparsers()
    set_branch = subparsers.add_parser('set-branch', help="update a local git repository to a given branch if it exists on the remote")
    get_branch = subparsers.add_parser('get-branch', help="get the current branch of a local git repository")

    get_branch.add_argument("repository", nargs="?", help="the local repository directory to update", default=os.getcwd())
    get_branch.set_defaults(cmd=_get_branch_cmd)

    set_branch.add_argument("--branch", help="the name of the branch to sync to if it exists", required=True)
    set_branch.add_argument("repository", help="the local repository directory to update")
    set_branch.set_defaults(cmd=_set_branch_cmd)

    return parser


def main():
    cli = create_cli()
    opts = cli.parse_args()
    opts.repository = os.path.abspath(opts.repository)
    print(opts)
    opts.cmd(opts)


if __name__ == '__main__':
    main()
