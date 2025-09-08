#!/usr/bin/env python3
"""
Rewritten SSE stream parser for Anthropic API responses.

This script reads from request.txt and extracts JSON data from Server-Sent Events,
focusing on content_block_start and content_block_delta events to reconstruct
the complete JSON payload.
"""

import json
import sys
from pathlib import Path


def parse_sse_file(filename):
    """
    Parse SSE data from a file and extract JSON content.
    
    Args:
        filename (str): Path to the file containing SSE data
        
    Returns:
        str: Concatenated JSON string from content blocks
    """
    blocks = []
    
    try:
        with open(filename, 'r', encoding='utf-8') as file:
            accumulated = {
                "content_block_type": "",
                "accumulated_json": ""
            }

            for line_num, line in enumerate(file, 1):
                line = line.strip()
                
                # Skip lines that don't start with "data:"
                if not line.startswith("data:"):
                    continue
                
                # Extract JSON after "data: "
                json_str = line[5:].strip()  # Remove "data:" prefix
                
                if not json_str:
                    continue
                
                try:
                    # Parse the JSON
                    data = json.loads(json_str)
                    
                    # Check the type field
                    event_type = data.get("type", "")
                    
                    if event_type == "content_block_start":
                        # Extract content_block field
                        content_block = data.get("content_block", {})
                        if "type" in content_block:
                            accumulated["content_block_type"] = content_block["type"]
                            if content_block["type"] == "tool_use":
                                name = content_block["name"]
                                accumulated["content_block_type"] += f"({name})"
                    
                    elif event_type == "content_block_delta":
                        # Extract delta.partial_json field
                        delta = data.get("delta", {})
                        partial_json = delta.get("partial_json", "")
                        if partial_json:
                            accumulated["accumulated_json"] += partial_json
                        
                        text = delta.get("text", "")
                        if text:
                            accumulated["accumulated_json"] += text
                        
                        thinking = delta.get("thinking", "")
                        if thinking:
                            accumulated["accumulated_json"] += thinking
                    
                    elif event_type == "content_block_stop":
                        blocks.append(accumulated)
                        accumulated = {
                            "content_block_type": "",
                            "accumulated_json": ""
                        }
                    
                    # Ignore all other types
                    
                except json.JSONDecodeError as e:
                    print(f"Warning: Failed to parse JSON on line {line_num}: {e}", file=sys.stderr)
                    print(f"Raw line: {line}", file=sys.stderr)
                    continue
    
    except FileNotFoundError:
        print(f"Error: File '{filename}' not found.", file=sys.stderr)
        return ""
    except Exception as e:
        print(f"Error reading file '{filename}': {e}", file=sys.stderr)
        return ""
    
    return blocks


def main():
    """Main function to process the SSE file and output results."""
    filename = sys.argv[1]
    
    # Check if file exists
    if not Path(filename).exists():
        print(f"Error: File '{filename}' not found in current directory.", file=sys.stderr)
        sys.exit(1)
    
    # Parse the SSE file
    blocks = parse_sse_file(filename)

    for block in blocks:
        accumulated_json = block["accumulated_json"]
        content_block_type = block["content_block_type"]
        
        print(f"---------------------------------------------------------------\nContent block type: {content_block_type}\n")
        
        # Try to parse the accumulated JSON
        try:
            parsed_json = json.loads(accumulated_json)
            print(json.dumps(parsed_json, indent=2, ensure_ascii=False))
        except json.JSONDecodeError as e:
            print(accumulated_json.replace("\\n", "\n"))


if __name__ == "__main__":
    main()
